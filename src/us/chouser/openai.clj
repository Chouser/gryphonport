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

(defonce *world
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

(defn parse-content [resp]
  (let [content (-> resp :body-map :choices first :message :content)
        [_ id adj desc] (re-matches #"(?s)location id (\w+), [^\n]+:\n((?:  to the [^\n]+\n)+)\s*(.*)"
                                    content)]
    (when-not id
      (throw (ex-info "Parse failed" {})))
    {:id id
     :adj (->> (re-seq #"(?m)^  to the (\w+): id (\w+), (.*)" adj)
               (map (fn [[_ dir nid title]]
                      {:dir dir :nid nid :title title})))
     :desc desc}))

(defn merge-world [world id new-loc]
  (assert (= id (:id new-loc)))
  (reduce (fn [world {:keys [dir nid title]}]
            (if-let [old-loc (get world nid)]
              (do
                (when-not (= title (:title old-loc))
                  (println "WARN: title change ignored:"
                           nid "is" (:title old-loc) "not" title))
                world)
              (assoc world nid {:title title})))
          (assoc-in world [id :description] (:desc new-loc))
          (:adj new-loc)))

;;== interactive

(defonce *my-loc-id (atom nil))

(defn describe []
  (let [id @*my-loc-id]
    (if-let [d (get-in @*world [id :description])]
      (println d)
      (let [p (parse-content (chat {:msgs (full-prompt @*world id)}))]
        (swap! *world merge-world id p)
        (write-world)
        (println (:desc p))))))

(defn begin []
  (reset! *my-loc-id (rand-nth (keys @*world)))
  (describe))

(defn go [dir]
  (when-let [dxdy (get dirs dir)]
    (swap! *my-loc-id #(apply-id-delta (parse-id %) dxdy))
    (describe)))

;; If a box has sub-boxes, you must be in a specific sub-box
;; For a building, each linked section is linked to some room in the building.
;; Each room can be linked to zero or one section.
#_
(defn graph-errors [graph]
  (concat
   ;; no directed edges
   (for [[id1 {:keys [adj]}] graph
         id2 adj
         :when (not (get-in graph [id2 :adj id1]))]
     [:missing-adj id2 id1])
   ;; no edges to self
   (for [[id {:keys [adj]}] graph
         :when (get-in graph [id :adj id])]
     [:bad-adj id id])))

#_
(defn fix-errors [graph errors]
  (reduce (fn [graph [err-type & args]]
            (case err-type
              :missing-adj (update-in
                            graph [(first args) :adj] conj (second args))))
          graph
          errors))

;; room 10 -> building/town section 11 -> town/district 12 -> region (area?) 13
(def graph
  {:nodes
   {:a001 {:name "Gryphon"
           :level 13
           :parent nil
           :children? true}
    :d002 {:name "Gryphonport"
           :level 12
           :parent :a001
           :children? true}
    :d003 {:name "Tangled Forest"
           :level 12
           :parent :a001
           :children? true}

    :s003 {:name "Market Square"
           :level 11
           :parent :d002}
    :s004 {:name "Town Hall"
           :level 11
           :parent :d002}
    :s005 {:name "Blacksmith's Forge"
           :level 11
           :parent :d002}
    :s006 {:name "Tavern"
           :level 11
           :parent :d002}
    :s007 {:name "Temple of the Divine"
           :level 11
           :parent :d002}
    :s008 {:name "Dockside"
           :level 11
           :parent :d002}
    :s009 {:name "Keep"
           :level 11
           :parent :d002}
    :s010 {:name "Alchemist's Shop"
           :level 11
           :parent :d002}
    :s011 {:name "Farmstead"
           :level 11
           :parent :d002}
    :s012 {:name "Graveyard"
           :level 11
           :parent :d002}

    :r013 {:name "Entrance Hall"
           :level 10
           :parent :s004}
    :r014 {:name "Council Chamber"
           :level 10
           :parent :s004}
    :r015 {:name "Archives"
           :level 10
           :parent :s004}
    :r016 {:name "Guard Room"
           :level 10
           :parent :s004}
    :r017 {:name "Treasury"
           :level 10
           :parent :s004}
    :r018 {:name "Jail"
           :level 10
           :parent :s004}}
   :adj #{#{:r013 :r014}
          #{:r013 :r015}
          #{:r013 :r016}
          #{:r013 :s003}
          #{:r015 :r014}
          #{:r017 :r016}
          #{:r018 :r016}
          #{:s003 :s004}
          #{:s003 :s006}
          #{:s003 :s007}
          #{:s003 :s010}
          #{:s005 :s003}
          #{:s005 :s010}
          #{:s006 :s004}
          #{:s007 :s012}
          #{:s008 :s006}
          #{:s009 :r016}
          #{:s009 :s004}
          #{:s011 :s003}
          #{:d002 :d003}
          #{:s011 :d003}}})

;; sections (including buildings)
;; districts (including towns)
(def level-names
  {11 {:id-type "section", :part "room", :parts "rooms"}
   12 {:id-type "district", :part "section", :parts "sections"}
   13 {:id-type "region", :part "district", :parts "districts"}})

;; Add a short description of each node? To help coherence during graph generation?
(defn gen-user [graph id]
  (let [m (get-in graph [:nodes id])
        id-name (:name m)
        parent (get-in graph [:nodes (:parent m) :name])
        {:keys [id-type part parts]} (level-names (:level m))]
    (str "1. List the "parts" of "id-name", a "id-type" in "parent".
2. Then for each "part", choose one or more of the other "parts" to be adjacent to it.
3. Finally, for each "id-type" that is adjacent to "id-name", choose a unique "part" to be the exit to that "id-type".")))



(defn gen-assistant [graph id]
  (let [m (get-in graph [:nodes id])
        id-name (:name m)
        {:keys [id-type part parts]} (level-names (:level m))
        part-pairs (filter #(= id (:parent (val %))) (:nodes graph))
        part-ids (set (keys part-pairs))
        adj-pairs (filter #(some part-ids %) (:adj graph))
        levels (->> (:adj graph)
                    (filter #(some part-ids %)) ;; edges of our parts
                    (map #(sort-by :level (map (:nodes graph) %)))
                    (group-by #(mapv :level %)))]
    (->>
     ["=== " parts " of " id-name "\n"
      (for [cm (vals part-pairs)]
        [(:name cm) "\n"])
      "\n=== adjacent " parts "\n"
      (for [[a b] (get levels [(dec (:level m)) (dec (:level m))])]
        [(:name a) " to " (:name b) "\n"])
      "\n=== " parts " adjacent to another " id-type "\n"
      (for [[a b] (get levels [(dec (:level m)) (:level m)])]
        [(:name a) " to " (:name b) " of " (get-in graph [:nodes (:parent b) :name]) "\n"])]
     flatten (apply str))))

;; 1. List the sections (including buildings) of Gryphonport, a town in Gryphon, with a short description of each.
;; 2. Then for each section, list the other sections adjacent to it.
;; 3. For each district that is adjacent to Gryphonport, choose a unique section to be the exit to that district.

;; 1. List the rooms of the Town Hall of Gryphonport, with a short description of each.
;; 2. Then for each room, list the other rooms adjacent to it.
;; 3. For each section that is adjacent to the Town Hall, choose a unique room to be the exit to that section.

;;  Entrance Hall: The Market Square
;;  Guard Room: The Keep
;;  Jail: The Graveyard

;;  Entrance Hall     Council Chamber, Archives, and Guard Room.
;;  Council Chamber   Entrance Hall and Archives.
;;  Archives          Entrance Hall, Council Chamber, Guard Room, Treasury, and Jail.
;;  Guard Room        Entrance Hall, Archives, and Jail.
;;  Treasury          Archives.
;;  Jail              Guard Room and Archives.

;;  Market Square: Town Hall, Blacksmith's Forge, Temple of the Divine, and Tavern.
;;  Town Hall: Market Square, Tavern, and Keep.
;;  Blacksmith's Forge: Market Square and Alchemist's Shop.
;;  Tavern: Market Square, Town Hall, Dockside, and Farmstead.
;;  Temple of the Divine: Market Square and Graveyard.
;;  Dockside: Tavern and Boat Dock (leads to other locations by boat).
;;  Keep: Town Hall and Battlements (leads to the town's defenses).
;;  Alchemist's Shop: Blacksmith's Forge and Market Square.
;;  Farmstead: Tavern and Fields (leads to a rural area).
;;  Graveyard: Temple of the Divine and Catacombs (leads to a dungeon or underground area).

(comment

  (pprint (sort (fix-errors graph (graph-errors graph))))

  (pprint-msgs (full-prompt @*world "50_52"))

  (def resp (chat {:msgs (full-prompt @*world "50_52")}))

  (merge-world @*world "50_52" (parse-content resp))

  (swap! *world merge-world "50_52" (parse-content resp))
  (write-world)

  (println (-> resp :body-map :choices first :message :content println))
  )
