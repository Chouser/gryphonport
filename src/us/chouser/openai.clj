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

(defn log-filename [time]
  (io/file "log"
           (-> time
               (.truncatedTo ChronoUnit/SECONDS)
               (.format DateTimeFormatter/ISO_DATE_TIME)
               (str ".edn"))))

(defn chat [{:keys [body-map msgs] :as request}]
  (let [req (->> (dissoc request :msgs :body-map)
                 (merge
                  {:url "https://api.openai.com/v1/chat/completions"
                   :method :post
                   :headers {"Content-Type" "application/json"
                             "Authorization" (str "Bearer " (get-secret :openai-key))}
                   :body (json/write-str
                          (merge {:model "gpt-3.5-turbo",
                                  :messages (map (fn [[role & content]]
                                                   {:role (name role)
                                                    :content (apply str content)})
                                                 msgs)
                                  :temperature 0.7}
                                 body-map))}))
        response (http/request req)]
    (with-open [log (io/writer (log-filename (ZonedDateTime/now ZoneOffset/UTC)))]
      (binding [*out* log]
        (pprint {:request req
                 :response response})))
    (assoc response
           :body-map (try (json/read-str (:body response))
                          (catch Exception ex nil)))))

(comment
  (chat {:msgs [[:system "You are an interactive fiction game author, "
                 "describing the scenes and objects of a game."]
                [:user "CURRENT SCENE: the back yard\n"
                 "EXIT NORTH: the garden\n"
                 "EXIT EAST: the porch\n"
                 "OBJECTS: firepit, fire poker\n"
                 "DESCRIBE: the back yard"]
                [:assistant "As you stand in the back yard, you feel the cool grass beneath your feet and the warmth of the sun on your skin. You notice the sound of birds chirping in the distance, and the gentle rustling of leaves from the trees that surround the yard.

In the center of the yard, there is a firepit made of stacked stones, with blackened wood ashes scattered around it. You can see the glint of a fire poker leaning against the side of the pit, still warm from its previous use. The firepit seems to have been recently used, as the scent of wood smoke still lingers in the air.

To the east, you can see a wooden porch. To the north, you see a beautifully tended garden.

The back yard is a tranquil and serene space, offering a peaceful escape from the hustle and bustle of everyday life."]
                [:user "CURRENT SCENE: the back yard\n"
                 "EXIT NORTH: the garden\n"
                 "EXIT EAST: the porch\n"
                 "OBJECTS: firepit, fire poker\n"
                 "DESCRIBE: fire poker"]
                [:assistant "The fire poker is a three-foot-long metal rod with a slightly curved and tapered tip, used for stirring and rearranging logs in the fire pit. It has a polished surface and a comfortable, heat-resistant handle. The poker is durable, well-maintained, and radiates warmth from its recent use."]
                [:user "CURRENT SCENE: the porch\n"
                 "EXIT WEST: the back yard\n"
                 "EXIT EAST: the house\n"
                 "OBJECTS: bench, table\n"
                 "DESCRIBE: the porch"]
                [:assistant "On the porch, the smooth wooden planks creak gently under your weight, welcoming you to this cozy space. Looking west from the porch, you can see the beautiful backyard in all its glory. The porch is elevated just enough to give you a perfect view of the yard, allowing you to observe the vibrant flowers and hear the gentle sounds of nature.

There is a small wooden table in the center of the porch, adorned with a vase of freshly cut flowers that adds a touch of natural beauty to the space. A bench faces the table; it looks inviting.

A door on the east side of the porch opens into the house."]
                [:user "CURRENT SCENE: the garden\n"
                 "EXIT SOUTH: the back yard\n"
                 "EXIT NORTH: the sidewalk\n"
                 "OBJECTS: iron bench\n"
                 "DESCRIBE: the garden"]]})
  )
