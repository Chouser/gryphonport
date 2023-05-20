(ns us.chouser.gryphonport.location-descriptions
  (:require [us.chouser.gryphonport.location-graph :as loc]
            [us.chouser.gryphonport.util :as util]
            [clojure.string :as str]))

(defn gen-user [graph id]
  (let [title (fn [n]
                [(:name n) " (a " (:kind n)
                 #_(when-let [p (:parent n)]
                   [" of " (:name (loc/node graph p))])
                 ")"])
        n (loc/node graph id)]
    (util/fstr
     ["Describe "(title n)" of "(:name (loc/node graph (:parent n)))" as a storyteller."
      (if (:children? n)
        ;; Location summary that is never displayed directly to players:
        (let [parts (loc/get-parts graph id)]
          (assert (seq parts) "populate graph before describing")
          [" It's the kind of place that would contain "
           (util/flist ", " "and " (map :name parts))
           (when-let [adj (loc/peer-adjacent-nodes graph id)] ;; adjacent-ids might be ok here
             [", and be near "
              (->> adj
                   (map title)
                   (util/flist ", " "and "))])
           "."])
        ;; Childless location descriptions will be shown to players:
        [" Be sure to mention that from here the only places you can go are "
         (->> (loc/nav-adj-node graph id)
              (map title)
              (util/flist ", " "or "))
         "."])])))

(defn gen-assistant [graph id]
  (:description (loc/node graph id)))

(defn prompt-msgs [graph id]
  (prn :prompt-loc-description id)
  (let [example-ids
        , (->> (concat [:r013 :r016 :s003]
                       (loc/path-from-root graph id)
                       (loc/adjacent-ids graph id))
               distinct
               (filter #(:description (loc/node graph %)))
               (take-last 10))]
    (concat
     [[:system "The assistant is describing a fictional world."]]
     (->> example-ids
          (mapcat (fn [eid]
                    [[:user (gen-user graph eid)]
                     [:assistant (gen-assistant graph eid)]])))
     [[:user (gen-user graph id)]])))

(defn merge-descriptions [graph description-map]
  (reduce (fn [m [k d]]
            (assoc-in m [:nodes k :description] d))
          graph
          description-map))

(defn describe-down-to [world loc]
  (reduce (fn [world loc]
            (if-let [desc (:description (loc/node world loc))]
              world
              (merge-descriptions
               world
               {loc (util/content
                     (util/chat {:msgs (prompt-msgs world loc)}))})))
          world
          (concat (loc/path-from-root world loc)
                  [loc])))

(defn move-thing [world loc-path to-loc]
  (let [n (loc/node world to-loc)
        orig-loc (get-in world loc-path)]
    (if (not (:children? n))
      (-> world
          (describe-down-to to-loc)
          (assoc-in loc-path to-loc))
      (let [world (if (seq (loc/get-parts world to-loc))
                    world
                    (do
                      (prn :populate to-loc)
                      (->> (util/chat {:msgs (loc/prompt-msgs world to-loc)})
                           util/content
                           loc/parse-content
                           (loc/id-content world to-loc)
                           (loc/merge-subgraph world))))]
        (recur world loc-path (loc/find-gateway world orig-loc to-loc))))))
