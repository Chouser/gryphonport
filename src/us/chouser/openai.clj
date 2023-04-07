(ns us.chouser.openai
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [clojure.string :as str])
  (:import (java.io PushbackReader)
           (java.time ZonedDateTime ZoneOffset)
           (java.time.temporal ChronoUnit)
           (java.time.format DateTimeFormatter)))

(def get-secret
  (partial get (read (PushbackReader. (io/reader "secrets.edn")))))

(def *world
  (atom (->> "us/chouser/world.edn" io/resource io/reader PushbackReader.
             read (into {}))))

(defn write-world []
  (with-open [w (io/writer (io/resource "us/chouser/world.edn"))]
    (binding [*out* w]
      (pprint (sort @*world)))))

(defn log-filename [time]
  (io/file "log"
           (-> time
               (.truncatedTo ChronoUnit/SECONDS)
               (.format DateTimeFormatter/ISO_DATE_TIME)
               (str ".edn"))))

(defn format-msgs [msgs]
  (map (fn [[role & content]]
         {:role (name role)
          :content (apply str (flatten content))})
       msgs))

(defn pprint-msgs [msgs]
  (run! (fn [[role & content]]
          (println "==" role)
          (println (apply str (flatten content))))
        msgs))

(defn chat [{:keys [body-map msgs] :as request}]
  (let [req (->> (dissoc request :msgs :body-map)
                 (merge
                  {:url "https://api.openai.com/v1/chat/completions"
                   :method :post
                   :headers {"Content-Type" "application/json"
                             "Authorization" (str "Bearer " (get-secret :openai-key))}
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
    (assoc response
           :body-map (try (json/read-str (:body response) :key-fn keyword)
                          (catch Exception ex nil)))))

(def dirs
  {:north [0 1]
   :south [0 -1]
   :east  [1 0]
   :west  [-1 0]})

(defn parse-id [id]
  (->> id (re-matches #"(\d+)_(\d+)") rest (map #(Long/parseLong %))))

(defn apply-id-delta [[x y] [dx dy]]
  (str (+ x dx) "_" (+ y dy)))

(defn neighbors [id]
  (let [xy (parse-id id)]
    (for [[dir dxdy] dirs]
      [dir (apply-id-delta xy dxdy)])))

(defn format-loc [world id mode]
  (->> ["location id " id ", " (get-in world [id :title]) ":\n"
        (for [[dir nid] (neighbors id)]
          ["  to the " (name dir) ": id " nid ", "
           (let [title (get-in world [nid :title])]
             (case mode
               :user-example (if (zero? (rand-int 2)) "UNKNOWN" title)
               :assistant (or title "BLOCKED")
               :user-final (or title "UNKNOWN")))
           "\n"])]))

(def user-suffix
  "Invent names for neighbornig UNDEFINED locations and describe this location.")

(defn full-prompt [world id]
  (concat
   [[:system "You are an interactive fiction game author, "
     "describing the scenes and objects of a game."]]
   (->> (concat (map second (neighbors id))
                (shuffle (keys world)))
        (mapcat #(when-let [desc (get-in world [% :description])]
                   [[:user (format-loc world % :user-example) user-suffix]
                    [:assistant [(format-loc world % :assistant)
                                 desc]]]))
        (take 6)) ;; must be even, at least double the possible neighbors
   [[:user (format-loc world id :user-final) user-suffix]]))

(comment
  (pprint-msgs (full-prompt @*world "50_52"))

  (def resp (chat {:msgs (full-prompt @*world "50_52")}))

  (println (-> resp :body-map :choices first :message :content println))

  {:body-map
   {:id "chatcmpl-72Yx7TltAdJrm3wFVLApSimonSVAE",
    :object "chat.completion",
    :created 1680845369,
    :model "gpt-3.5-turbo-0301",
    :usage {:prompt_tokens 1113, :completion_tokens 310, :total_tokens 1423},
    :choices
    [{:message
      {:role "assistant",
       :content
       "location id 50_52, the greenhouse:\n  to the north: id 50_53, the propagation room\n  to the south: id 50_51, the garden\n  to the east: id 51_52, the potting station\n  to the west: id 49_52, the storage room\n\nAs you step into the greenhouse, you are surrounded by the lush greenery of various plants, most of which appear to be thriving in the warm and humid environment. The greenhouse is spacious and airy, with glass walls and roof that let in plenty of sunlight.\n\nTo the north is a smaller room that you can see through a glass door. It looks like a propagation room, where young seedlings are nurtured and brought to maturity before being transplanted into the main greenhouse.\n\nTo the east is an area with a large potting station, where plants are potted and repotted as needed. You can see a variety of pots, soil, and gardening tools in this area.\n\nTo the west is a storage room, where gardening supplies are kept. From the glimpse you get through the partially open door, you can see shelves lined with pots, fertilizer, and other gardening essentials.\n\nTo the south is the garden, visible through the glass walls of the greenhouse. The garden's beauty is amplified through the glass walls of the greenhouse, and you can see the flowers and plants swaying gently in the breeze.\n\nThe greenhouse is a peaceful and serene space, filled with the beauty and magic of nature."},
      :finish_reason "stop",
      :index 0}]}}
  )
