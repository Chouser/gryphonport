(ns us.chouser.gryphonport.location-graph
  (:require [us.chouser.gryphonport.util :refer [fstr]]
            [clojure.string :as str]))

;; If a box has sub-boxes, you must be in a specific sub-box
;; For a building, each linked section is linked to some room in the building.
;; Each room can be linked to zero or one section.
;; No actual children, but `:children? true` means you're incomplete.

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

;; Add a short description of each node? To help coherence during graph generation?
(defn gen-user [graph id]
  (let [m (node graph id)
        peer-adj (peer-adjacent-nodes graph id)]
    (fstr
     ["List the sub-locations of "(:name m)", a "(:kind m)" in " (->> m :parent (node graph) :name)
      (when (= "town" (:kind m))
        ", including streets, buildings, and public areas")
      ". "
      "Then for each of these locations, list one or more adjacent locations. "
      "Locations should only be adjacent if they are related in purpose or physically connected, and never if both have sub-locations. "
      (when (seq peer-adj)
        ["Finally, since "(:name m)" is adjacent to " (interpose ", " (map #(:name %) peer-adj))
         ", for each of these choose one room or non-building in "(:name m)" to be an exit to it."])])))

(defn gen-assistant [graph id]
  (let [m (node graph id)
        parts (get-parts graph id)
        part-id-set (set (map :id parts))
        peer-adj (peer-adjacent-nodes graph id)]
    (fstr
     ["=== locations in " (:name m)"\n"
      (for [cm parts]
        [(:name cm) ", a " (:kind cm) " (contains " (when-not (:children? cm) "no ")
         "sub-locations)\n"])
      "\n=== adjacent locations in " (:name m)"\n"
      (->> (:adj graph)
           (filter #(every? part-id-set %))
           (group-by first)
           (map (fn [[a pairs]]
                  ["A player in "(:name (node graph a)) " can go to any of: "
                   (interpose ", " (map #(:name (node graph (second %))) pairs))
                   "\n"])))
      (when (and (:children? m) peer-adj)
        ["\n=== exits out of " (:name m)"\n"
         (->> peer-adj
              (map (fn [peer]
                     ["Exit to "(:name peer)
                      " of " (->> peer :parent (node graph) :name)
                      " from "
                      ;; The part of id that is adjacent to peer; should be exactly 1
                      (let [gws (filter part-id-set (adjacent-ids graph (:id peer)))]
                        (if (not= 1 (count gws))
                          (throw (ex-info "Bad gateway count"
                                          {:id id :node m, :peer-adj peer-adj, :peer peer, :gateways gws}))
                          (->> gws first (node graph) :name)))
                      "\n"])))])])))

(defn parse-content [content-str]
  (let [blocks (str/split content-str #"(?m)^=== ")
        m (into {} (map #(when-let [k (re-find #"^\w+" %)] [k %]) blocks))]
    {:locations (->> (get m "locations")
                     (re-seq #"\n(.*), a\w* ([^(]*) \(contains (no )?")
                     (map (fn [[_ node-name kind no-kids?]]
                            {:name node-name
                             :kind kind
                             :children? (not no-kids?)})))
     :adjacent (->> (get m "adjacent")
                    (re-seq #"A player in (.*) can go to any of: (.*)")
                    (mapcat (fn [[_ x ys]]
                              (->> (str/split ys #",\s+")
                                   (map (fn [y] #{x y})))))
                    set)
     :exits (some->> (get m "exits")
                     (re-seq #"Exit to (.*) of (.*) from (.*)")
                     (map (fn [[_ adj parent child]]
                            {:adj adj :parent parent :child child})))}))

(defn rand-id []
  (keyword (str (char (+ 97 (rand-int 26)))
                (char (+ 97 (rand-int 26)))
                (+ 100 (rand-int 900)))))

(defn id-content [graph id parsed]
  (let [self-node (node graph id)
        new-nodes (->> (:locations parsed)
                       (map #(assoc % :parent id))
                       (zipmap (repeatedly rand-id)))
        new-name-ids (zipmap (map :name (vals new-nodes))
                             (keys new-nodes))
        peer-adj-ids (->> (peer-adjacent-nodes graph id)
                          (map (fn [n] [(:name n) (:id n)]))
                          (into {}))]
    {:nodes new-nodes
     :adj (set (concat
                (->> (:exits parsed)
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
        example-ids (distinct
                     (concat [:d002 :s004]
                             (->> path
                                  (keep (fn [aid]
                                          (->> (parts-map aid)
                                               (remove path-set)
                                               (filter parts-map)
                                               shuffle
                                               first))))
                             path))]
    (concat
     [[:system "We are world-building"]
      [:user "You are world-building for a game where players can move between adjacent locations. Towns are locations that contain sub-locations, some of which are buildings. Buildings are locations that have sub-locations, all of which are rooms. No other locations contain any sub-locations."]
      [:assistant "ok"]]
     (->> example-ids
          (mapcat (fn [id]
                    [[:user (gen-user graph id)]
                     [:assistant (gen-assistant graph id)]])))
     [[:user (str/replace (gen-user graph id)
                          #"the parts"
                          "the many parts")]])))
