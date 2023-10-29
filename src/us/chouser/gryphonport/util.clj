(ns us.chouser.gryphonport.util
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str])
  (:import (java.io PushbackReader)
           (java.time ZonedDateTime ZoneOffset)
           (java.time.temporal ChronoUnit)
           (java.time.format DateTimeFormatter)))

(set! *warn-on-reflection* true)

(def default-cache-key "1")
(def request-cache-dir "log")

(defn float-array-to-str [^floats v]
  (let [b (java.nio.ByteBuffer/allocate (* 4 (count v)))]
    (dotimes [i (count v)]
      (.putFloat b (aget v i)))
    (with-open [baos (java.io.ByteArrayOutputStream.)
                gzos (java.util.zip.GZIPOutputStream. baos)]
      (doto gzos
        (.write (.array b))
        (.finish))
      (.encodeToString (java.util.Base64/getEncoder) (.toByteArray baos)))))

(defn str-to-float-array [^String compressed-base64]
  (with-open [gzip-input-stream (->> (.getBytes compressed-base64 "utf-8")
                                     (.decode (java.util.Base64/getDecoder))
                                     java.io.ByteArrayInputStream.
                                     java.util.zip.GZIPInputStream.)
              byte-array-output-stream (java.io.ByteArrayOutputStream.)]
    (io/copy gzip-input-stream byte-array-output-stream)
    (let [float-buffer (->> byte-array-output-stream
                            .toByteArray
                            java.nio.ByteBuffer/wrap
                            .asFloatBuffer)
          decompressed-floats (float-array (.remaining float-buffer))]
      (.get float-buffer decompressed-floats)
      decompressed-floats)))

(set! *warn-on-reflection* false)

(deftype Embedding [v]
  Object
  (equals [_ o]
    (or (and (instance? Embedding o) ;; fast case
             (= v (.v ^Embedding o)))
        ;; support reloading case:
        (and (= "us.chouser.gryphonport.util.Embedding" (.getName (class o)))
             (= v (.-v o)))))
  (toString [_] (float-array-to-str v)))

(set! *warn-on-reflection* true)

(defmethod print-method Embedding [o ^java.io.Writer w]
  (.write w (str "#" `embedding \space \" o \")))

(def get-secret
  (partial get (read (PushbackReader. (io/reader "secrets.edn")))))

(defn log-filename [md5 ^ZonedDateTime time]
  (io/file request-cache-dir
           (-> time
               (.truncatedTo ChronoUnit/SECONDS)
               (.format DateTimeFormatter/ISO_DATE_TIME)
               (str "-" md5".edn"))))

(defn read-file [file-path init]
  (let [r (io/file file-path)]
    (if (.exists r)
      (->> r io/reader PushbackReader.
           (edn/read {:default (fn [_ v] v)
                      :readers {`embedding #(Embedding. (str-to-float-array %))}}))
      init)))

(defn write-file [file-path data]
  (let [f (io/file file-path)]
    (when (.exists f)
      (when-not (.renameTo f (io/file (str file-path ".bak")))
        (println "WARNING: backup failed")))
    (with-open [w (io/writer f)]
      (binding [*out* w]
        (pprint data)))))

(defn make-resource-obj
  "Create an atom (or other watchable object) linked to a file on the classpath.
  The object will be initiallized with the current edn contents of the file (or
  init if the file doesn't exist), and any time the object is update the file
  will be written with the new pprint'ed value.

  Example: (defonce data (make-resource-obj atom \"us/chouser/data.edn\"))"
  [ctor file-path init]
  (let [obj (ctor (read-file file-path init))]
    (add-watch obj ::make-resource-obj
               (fn [_ _ _ new-value]
                 (write-file file-path new-value)))
    obj))

(defn fstr [& coll]
  (let [sb (StringBuilder. "")]
    (letfn [(walk [x]
              (cond
                (not (coll? x)) (.append sb (str x))
                (sequential? x) (run! walk x)
                :else (throw (ex-info (str "Bad fstr element: " (pr-str x))
                                      {:bad x}))))]
      (walk coll)
      (str sb))))

(defn flist [sep1 sep2 coll]
  (let [result
        (if-not (next coll)
          (first coll)
          (concat (interpose sep1 (drop-last coll))
                  [sep1 sep2] ;; oxford comma!
                  (last coll)))]
    (assert (every? seq coll) (apply str result))
    result))

(defn assert-valid-msgs [msgs]
  (assert (= :system (ffirst msgs)))
  (assert (->> msgs (drop 1) (take-nth 2) (map first) (every? #(= :user %))))
  (assert (->> msgs (drop 2) (take-nth 2) (map first) (every? #(= :assistant %))))
  (assert (= :user (first (last msgs)))))

(defn format-msgs [msgs]
  #_(assert-valid-msgs msgs)
  (map (fn [[role-or-name & content]]
         (if-let [nom ({:example-user "example_user"
                        :example-assistant "example_assistant"} role-or-name)]
           {:role "system"
            :name nom
            :content (fstr content)}
           {:role (name role-or-name)
            :content (fstr content)}))
       msgs))

(defn pprint-msgs [msgs]
  #_(assert-valid-msgs msgs)
  (run! (fn [[role & content]]
          (println "==" role)
          (println (fstr content)))
        msgs))

(defn ^String get-md5-hash [^String input]
  (str/replace
   (->> (.getBytes input "utf-8")
        (.digest (java.security.MessageDigest/getInstance "MD5"))
        (.encodeToString (java.util.Base64/getEncoder)))
   "/" "_"))

(defn chat [{:keys [body-map msgs get-openai-key] :as request}]
  (let [get-openai-key (or get-openai-key #(get-secret :openai-key))
        req-body-str (json/write-str
                      (merge {:model "gpt-3.5-turbo",
                              :messages (format-msgs msgs)
                              :temperature 0.7}
                             body-map))
        req-md5 (get-md5-hash (str (or (:cache-key request) default-cache-key)
                                   " " req-body-str))
        [cache-file] (->> (file-seq (io/file request-cache-dir))
                          (remove #(neg? (.indexOf (str %) req-md5))))
        cached-response (when cache-file
                          (->> cache-file io/reader java.io.PushbackReader.
                               (edn/read {:default vector})
                               :response))]
    (if (and cache-file
             (not (contains? #{429} (:status cached-response))))
      (do
        (when (:prn-usage? request true)
          (binding [*out* *err*]
            (prn :cache-hit req-md5)))
        cached-response)
      ;; cache miss:
      (let [request
            , (-> request
                  (dissoc :msgs :body-map)
                  (->> (merge
                        {:url "https://api.openai.com/v1/chat/completions"
                         :method :post
                         :headers {"Content-Type" "application/json"
                                   "Authorization" (str "Bearer " (get-openai-key))}
                         :throw-exceptions false
                         :body req-body-str})))
            response (http/request request)
            response (assoc response
                            :body-map (try (json/read-str (:body response) :key-fn keyword)
                                           (catch Exception ex nil)))]
        (with-open [log (io/writer
                         (log-filename req-md5 (ZonedDateTime/now ZoneOffset/UTC)))]
          (binding [*out* log]
            (pprint {:request request
                     :response response})))
        (when (:prn-usage? request true)
          (binding [*out* *err*]
            (some-> response :body-map :usage (assoc :req-md5 req-md5) prn)))
        response))))

(defn chatm [msgs]
  (chat {:msgs msgs}))

(defn embed [{:keys [body-map input] :as request}]
  (let [response
        , (chat {:url "https://api.openai.com/v1/embeddings"
                 :body-map (merge {:model "text-embedding-ada-002"
                                   :input input}
                                  body-map)})]
    (assoc response :embedding (->> response :body-map :data first :embedding float-array Embedding.))))

(defn content [resp]
  (when (not= 200 (-> resp :status))
    (throw (ex-info (str "WARNING unexpected status " (:status resp))
                    {:response resp})))
  (let [choices (-> resp :body-map :choices)]
    (when (-> choices next)
      (println "WARNING multiple choices"))
    (let [choice (-> choices first)]
      (when (not= "stop" (-> choice :finish_reason))
        (println "WARNING unexpenced finish_reason:" (:finish_reason choice)))
      (-> resp :body-map :choices first :message :content))))

(comment
  (let [log-map
        (clojure.edn/read-string {:readers {'object identity}} (slurp "log/2023-04-22T18:12:33Z.edn"))]
    (-> log-map :request :body (json/read-str :key-fn keyword)

        #_clojure.pprint/pprint
        :messages
        (->> (map (fn [m] [(keyword (:role m)) [(:content m)]])))
        pprint-msgs
        )
    #_(println (-> log-map :response :body))))
