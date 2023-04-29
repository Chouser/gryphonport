(ns us.chouser.gryphonport.util
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io PushbackReader)
           (java.time ZonedDateTime ZoneOffset)
           (java.time.temporal ChronoUnit)
           (java.time.format DateTimeFormatter)))

(set! *warn-on-reflection* true)

(def get-secret
  (partial get (read (PushbackReader. (io/reader "secrets.edn")))))

(defn log-filename [^ZonedDateTime time]
  (io/file "log"
           (-> time
               (.truncatedTo ChronoUnit/SECONDS)
               (.format DateTimeFormatter/ISO_DATE_TIME)
               (str ".edn"))))

(defn fstr [coll]
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
  (assert-valid-msgs msgs)
  (map (fn [[role & content]]
         {:role (name role)
          :content (fstr content)})
       msgs))

(defn pprint-msgs [msgs]
  (assert-valid-msgs msgs)
  (run! (fn [[role & content]]
          (println "==" role)
          (println (fstr content)))
        msgs))

(defn chat [{:keys [body-map msgs] :as request}]
  (let [req (->> (dissoc request :msgs :body-map)
                 (merge
                  {:url "https://api.openai.com/v1/chat/completions"
                   :method :post
                   :headers {"Content-Type" "application/json"
                             "Authorization" (str "Bearer " (get-secret :openai-key))}
                   :throw-exceptions false
                   :body (json/write-str
                          (merge {:model "gpt-3.5-turbo",
                                  :messages (format-msgs msgs)
                                  :temperature 0.7}
                                 body-map))}))
        response (http/request req)]
    (with-open [log (io/writer (log-filename (ZonedDateTime/now ZoneOffset/UTC)))]
      (binding [*out* log]
        (pprint {:request req
                 :response response})))
    (let [response
          (assoc response
                 :body-map (try (json/read-str (:body response) :key-fn keyword)
                                (catch Exception ex nil)))]
      (binding [*out* *err*]
        (some-> response :body-map :usage prn))
      response)))

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
