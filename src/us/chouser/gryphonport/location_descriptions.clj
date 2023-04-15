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
        n (loc/node graph id)
        adj (loc/peer-adjacent-nodes graph id)]
    (util/fstr
     ["Describe "(title n)" of "(:name (loc/node graph (:parent n)))"."
      (when (:children? n)
        (let [parts (loc/get-parts graph id)]
          (assert (seq parts) "populate graph before describing")
          [" You may briefly summarize that it contains "
           (util/flist ", " "and " (map :name parts))
           (when (seq adj)
             [", and that it is near "
              (->> adj
                   (map title)
                   (util/flist ", " "and "))])
           ", but do not list all these locations."]))
      (when (and (not (:children? n)) (seq adj))
        [" Be sure to mention that from here you can go to "
         (->> adj
              (map title)
              (util/flist ", " "or "))
         "."])])))

(defn gen-assistant [graph id]
  (:description (loc/node graph id)))

(defn prompt-msgs [graph id]
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
