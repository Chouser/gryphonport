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

(set! *warn-on-reflection* true)

(def get-secret
  (partial get (read (PushbackReader. (io/reader "secrets.edn")))))

(defonce *world
  (atom (->> "us/chouser/world.edn" io/resource io/reader PushbackReader.
             read)))

(defn write-world []
  (with-open [w (io/writer (io/resource "us/chouser/world.edn"))]
    (binding [*out* w]
      (prn @*world))))

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

(defn format-msgs [msgs]
  (map (fn [[role & content]]
         {:role (name role)
          :content (fstr content)})
       msgs))

(defn pprint-msgs [msgs]
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

;; No parent means you're incomplete? You have a child, but some are missing
#_
(def graph
  {:nodes
   {:a001 {:name "Gryphon"
           :kind "region"
           :parent nil
           :children? true}
    :d002 {:name "Gryphonport"
           :kind "town"
           :parent :a001
           :children? true}
    :d003 {:name "Tangled Forest"
           :kind "district"
           :parent :a001
           :children? true}
    :s003 {:name "Market Square"
           :kind "section"
           :parent :d002}
    :s004 {:name "Town Hall"
           :kind "building"
           :parent :d002
           :children? true}
    :s005 {:name "Wilhelm's Forge"
           :kind "building"
           :parent :d002
           :children? true}
    :s006 {:name "Odd Duck"
           :kind "tavern"
           :parent :d002
           :children? true}
    :s007 {:name "Temple of the Divine"
           :kind "building"
           :parent :d002
           :children? true}
    :s008 {:name "Dockside"
           :kind "section"
           :parent :d002}
    :s009 {:name "Keep"
           :kind "building"
           :parent :d002
           :children? true}
    :s010 {:name "Alchemist's Shop"
           :kind "building"
           :parent :d002
           :children? true}
    :s011 {:name "Farmstead"
           :kind "section"
           :parent :d002
           :children? true}
    :s012 {:name "Graveyard"
           :kind "section"
           :parent :d002
           :children? true}

    :r013 {:name "Entrance Hall"
           :kind "room"
           :parent :s004}
    :r014 {:name "Council Chamber"
           :kind "room"
           :parent :s004}
    :r015 {:name "Archives"
           :kind "room"
           :parent :s004}
    :r016 {:name "Guard Room"
           :kind "room"
           :parent :s004}
    :r017 {:name "Treasury"
           :kind "room"
           :parent :s004}
    :r018 {:name "Jail"
           :kind "room"
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

(def graph
  {:nodes
   {:a001 {:name "Gryphon"           :kind "region"   :parent nil   :children? true}
    :d100 {:name "Blackwood Station" :kind "outpost"  :parent :a001 :children? true}
    :d101 {:name "Blackwood Forest"  :kind "forest"   :parent :a001 :children? true}
    :d102 {:name "Blackwood Trail"   :kind "road"     :parent :a001}
    :d103 {:name "Gryphonport"       :kind "large town"     :parent :a001 :children? true}
    :s102 {:name "Stable Yard"       :kind "section"  :parent :d100}
    :s103 {:name "Watchtower"        :kind "building" :parent :d100 :children? true}
    :r104 {:name "Kitchen"           :kind "room"     :parent :s103}
    :r105 {:name "Bunkroom"          :kind "room"     :parent :s103}
    :r106 {:name "Watchroom"         :kind "room"     :parent :s103}}
   :adj #{
          #{:d100 :d101}
          #{:d101 :s102}
          #{:s102 :s103}

          #{:s102 :r104}
          #{:r104 :r105}
          #{:r104 :r106}

          #{:d102 :s102}

          #{:d100 :d102}
          #{:d102 :d103}
          }})


#_
(->> graph :nodes vals
     (filter #(= :d100 (:parent %)))
     (run! #(println (str (:name %) ", a " (:kind %)))))

(defn node [graph id]
  (get-in graph [:nodes id]))

(defn adjacent-ids
  "All the places you can get to from id"
  [graph id]
  (->> (:adj graph)
       (filter #(get % id))
       (map #(first (disj % id)))))

(defn peer-adjacent-nodes
  "All the places you can get to from id that share id's parent"
  [graph id]
  (let [parent-id (->> id (node graph) :parent)]
    (->> (adjacent-ids graph id)
         (map #(assoc (node graph %) :id %))
         (filter #(= parent-id (:parent %))))))

(defn get-parts
  "All the parts (nodes) of id (with :id added)"
  [graph id]
  (->> (:nodes graph)
       (filter #(= id (:parent (val %))))
       (map #(assoc (val %) :id (key %)))))

(defn get-parts-map
  "A map of each node id to its part ids"
  [graph]
  (reduce (fn [m [id n]]
            (update m (:parent n) (fnil conj #{}) id))
          {}
          (:nodes graph)))

(defn content [resp]
  (when (not= 200 (-> resp :status))
    (println "WARNING unexpected status" (:status resp)))
  (let [choices (-> resp :body-map :choices)]
    (when (-> choices next)
      (println "WARNING multiple choices"))
    (let [choice (-> choices first)]
      (when (not= "stop" (-> choice :finish_reason))
        (println "WARNING unexpenced finish_reason:" (:finish_reason choice)))
      (-> resp :body-map :choices first :message :content))))

;; Add a short description of each node? To help coherence during graph generation?
(defn gen-user [graph id]
  (let [m (node graph id)
        peer-adj (peer-adjacent-nodes graph id)]
    (fstr
     ["List the parts of "(:name m)", a "(:kind m)" in " (->> m :parent (node graph) :name)". "
      "For each, indicate if it contains sub-locations people can enter.\n"
      (when (seq peer-adj)
        [(:name m)" is adjacent to " (interpose ", " (map #(:name %) peer-adj))
         "; so for each of these choose one appropriate part to be a gateway to it.\n"])
      "Finally, for each part, list one or more adjacent parts (a commutative property), "
      "primarily connecting parts that are related in purpose or physically near "
      "each other. No two parts that each have sub-locations may be adjacent."])))

(defn gen-assistant [graph id]
  (let [m (node graph id)
        parts (get-parts graph id)
        part-id-set (set (map :id parts))
        peer-adj (peer-adjacent-nodes graph id)]
    (fstr
     ["=== parts of " (:name m)"\n"
      (for [cm parts]
        [(:name cm) ", a " (:kind cm) " (contains " (when-not (:children? cm) "no ")
         "sub-locations)\n"])
      (when (and (:children? m) peer-adj)
        ["\n=== gateways out of " (:name m)"\n"
         (->> peer-adj
              (map (fn [peer]
                     [(:name peer) " in " (->> peer :parent (node graph) :name) ": "
                      ;; The part of id that is adjacent to peer; should be exactly 1
                      (let [gws (filter part-id-set (adjacent-ids graph (:id peer)))]
                        (if (not= 1 (count gws))
                          (throw (ex-info "Bad gateway count"
                                          {:id id :node m, :peer peer, :gateways gws}))
                          (->> gws first (node graph) :name)))
                      "\n"])))])
      "\n=== adjacent parts of " (:name m)"\n"
      (->> (:adj graph)
           (filter #(every? part-id-set %))
           (group-by first)
           (map (fn [[a pairs]]
                  [(:name (node graph a)) ": "
                   (interpose ", " (map #(:name (node graph (second %))) pairs))
                   "\n"])))])))

(defn parse-content [content-str]
  (let [blocks (str/split content-str #"(?m)^=== ")
        m (into {} (map #(when-let [k (re-find #"^\w+" %)] [k %]) blocks))]
    {:parts (->> (get m "parts")
                 (re-seq #"\n(.*), a\w* ([^(]*) \(contains (no )?")
                 (map (fn [[_ node-name kind no-kids?]]
                        {:name node-name
                         :kind kind
                         :children? (not no-kids?)})))
     :gateways (some->> (get m "gateways")
                        (re-seq #"\n(.*) in (.*): (.*)")
                        (map (fn [[_ adj parent child]]
                               {:adj adj :parent parent :child child})))
     :adjacent (->> (get m "adjacent")
                    (re-seq #"\n(.*): (.*)")
                    (mapcat (fn [[_ x ys]]
                              (->> (str/split ys #",\s+")
                                   (map (fn [y] #{x y})))))
                    set)}))

(defn rand-id []
  (keyword (str (char (+ 97 (rand-int 26)))
                (char (+ 97 (rand-int 26)))
                (+ 100 (rand-int 900)))))

(defn id-content [graph id parsed]
  (let [self-node (node graph id)
        new-nodes (->> (:parts parsed)
                       (zipmap (repeatedly rand-id)))
        new-name-ids (zipmap (map :name (vals new-nodes))
                             (keys new-nodes))
        peer-adj-ids (->> (peer-adjacent-nodes graph id)
                          (map (fn [n] [(:name n) (:id n)]))
                          (into {}))]
    {:nodes new-nodes
     :adj (set (concat
                (->> (:gateways parsed)
                     (map (fn [{:keys [adj parent child]}]
                            (assert (= parent (->> self-node :parent (node graph) :name)))
                            (let [upper-id (or (peer-adj-ids adj) (throw (ex-info "Bad peer-adj" {})))
                                  lower-id (or (new-name-ids child) (throw (ex-info "Bad gateway" {})))]
                              (assert (< (count
                                          (filter true?
                                                  [(:children? (new-nodes lower-id))
                                                   (:children? (node graph upper-id))]))
                                         2))
                              #{upper-id lower-id}))))
                (->> (:adjacent parsed)
                     (map (fn [name-pair]
                            (set (map new-name-ids name-pair))))
                     set)))}))

(defn merge-subgraph [graph m]
  {:nodes (merge-with #(throw (ex-info "Id collision" {:nodes %&}))
                      (:nodes graph) (:nodes m))
   :adj (into (:adj graph) (:adj m))})

(defn dot [graph]
  (let [parts-map (get-parts-map graph)
        leaves (->> (:nodes graph)
                    (filter (fn [[id n]]
                              (when (or (not (:children? n))
                                        (empty? (parts-map id)))
                                [id n])))
                    (into {}))
        walk (fn walk [prefix id]
               (let [n (node graph id)
                     label-snip ["label=" (pr-str (str (:name n) "\n" (:kind n) " " id))]]
                 (if (leaves id)
                   [prefix (name id) "["
                    "shape=" (if (:children? n) "box" "ellipse") ", "
                    label-snip
                    "];\n"]
                   [prefix "subgraph cluster_" (name id) " {\n"
                    prefix label-snip ";\n"
                    (map (partial walk (str prefix "  ")) (sort (parts-map id)))
                    prefix "}\n"]
                   )))]
    (fstr
     ["graph world {\n"
      (map (partial walk "  ") (parts-map nil))
      (->> (:adj graph)
           (map (fn [pair]
                  (when (and (leaves (first pair))
                             (leaves (second pair)))
                    ["  " (name (first pair)) " -- " (name (second pair)) ";\n"]))))
      "}\n"])))

(defn prompt-msgs [graph id]
  (let [parts-map (get-parts-map graph)
        _ (assert (empty? (parts-map id)))
        n (node graph id)
        _ (assert (:children? n))
        path (->> (iterate #(->> % (node graph) :parent) id)
                  (take-while identity)
                  next
                  reverse)
        path-set (set path)
        example-ids (concat (->> path
                                 (keep (fn [aid]
                                         (->> (parts-map aid)
                                              (remove path-set)
                                              (filter parts-map)
                                              shuffle
                                              first))))
                            path)]
    (prn example-ids)
    (concat
     [[:system "You are building a world of connected locations."]]
     (->> example-ids
          (mapcat (fn [id]
                    [[:user (gen-user graph id)]
                     [:assistant (gen-assistant graph id)]])))
     [[:user (str/replace (gen-user graph id)
                          #"the parts"
                          "the several parts")]])))

(comment
  (reset! *world graph)
  (write-world)
  (spit "world.dot" (dot @*world))

  (def resp
    (chat {:msgs
           [[:system "You are building a world of connected locations."]
            [:user (gen-user graph :d100)]
            [:assistant (gen-assistant graph :d100)]
            [:user (gen-user graph :s103)]
            [:assistant (gen-assistant graph :s103)]
            [:user (gen-user graph :a001)]
            [:assistant (gen-assistant graph :a001)]
            [:user (gen-user graph :d103)]]}))

  (pprint (sort (fix-errors graph (graph-errors graph))))

  (pprint-msgs (full-prompt @*world "50_52"))

  (def resp (chat {:msgs (full-prompt @*world "50_52")}))

  (merge-world @*world "50_52" (parse-content resp))

  (swap! *world merge-world "50_52" (parse-content resp))
  (write-world)

  (println (-> resp :body-map :choices first :message :content println))
  )
