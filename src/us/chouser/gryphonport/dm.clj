(ns us.chouser.gryphonport.dm
  (:require [us.chouser.gryphonport.util :as util]
            [us.chouser.gryphonport.location-graph :as loc]
            [clojure.string :as str]))

(def example
  [[:user {:p1 {:loc :s014
                :instructions "go to main"}}]
   [:assistant {:p1 {:reply "You stroll to Main Street"
                     :go :s003}}]
   [:user {:p1 {:loc :s003
                :instructions "go forge"}}]
   [:assistant {:p1 {:reply "You duck into Wilhelm's Forge"
                     :go :s005}}]
   [:user {:p1 {:loc :pq541
                :instructions "go to south street"}}]
   [:assistant {:p1 {:reply "South Steet is not adjacent. You might try Main Street instead."}}]
   [:user {:p1 {:loc :pq541
                :instructions "ask if anyone's here"}}]
   [:assistant {:p1 {:reply "You look around cautiously and say, \"Is anyone here?\""
                     :say "Is anyone here?"}}]])

;; TODO get rid of duplicate descriptions in dm-history prompt
(defn gen-user [world chars]
  (util/fstr
   [(->> chars
         (group-by #(-> % val :loc))
         (map (fn [[loc chars]]
                (let [loc-node (loc/node world loc)]
                  ["=== Description of " (:name loc-node) "\n"
                   (:description loc-node) "\n\n"
                   "=== Instruction context:\n"
                   "From " (:name loc-node) ", a player may only go to one of: "
                   (->> (loc/nav-adj-node world loc)
                        (map :name)
                        (util/flist ", " "or "))
                   ".\n\n"
                   (->> chars
                        (map (fn [[cid {:keys [instructions]}]]
                               ["Player " cid " is in "(:name loc-node)
                                ", and their instructions are: "
                                instructions
                                "\n"])))]))))
    "\nWhat should be the action and reply to each player, based on their instructions."]))

(defn gen-assistant [world chars]
  (util/fstr
   (->> chars
        (map (fn [[cid actions]]
               ["Player " cid " actions:\n"
                (->> actions
                     (map (fn [[verb arg :as action]]
                            ["  " (name verb) ": "
                             (case verb
                               :reply arg
                               :go (:name (loc/node world arg))
                               :say arg)
                             "\n"])))])))))

(defn parse-content [content-str]
  (let [blocks (re-seq #"(?m)^Player :(\w+) actions:((?:\n  .*)*)"
                       content-str)]
    (->> blocks
         (map (fn [[_ cid block]]
                [(keyword cid)
                 (->> (re-seq #"(?m)^  (\w+): (.*)" block)
                      (map (fn [[_ verb arg]]
                             [(keyword verb) arg]))
                      (into {}))]))
         (into {}))))

(defn id-content [world parsed]
  (->> parsed
       (map (fn [[cid actions]]
              [cid
               (cond-> actions
                 (:go actions)
                 , (update
                    :go (fn [arg]
                          (let [loc (get-in world [:characters cid :loc])
                                _ (assert loc (str "Couldn't find current loc for "
                                                   cid))
                                name-lid (->> (loc/nav-adj-node world loc)
                                              (map (juxt :name :id))
                                              (into {}))]
                            (prn :name-lid name-lid)
                            (or (name-lid arg)
                                (throw (ex-info "No loc id found"
                                                {:from loc
                                                 :to arg
                                                 :name-lid name-lid})))))))]))
       (into {})))

(defn add-instruction-locs [world char-instructions]
  (->> char-instructions
       (map (fn [[cid text]]
              [cid {:loc (get-in world [:characters cid :loc])
                    :instructions text}]))
       (into {})))

(defn prompt-msgs [world user-data]
  (concat
   [[:system "The user will provide enough context for the assistant to act as referee, choosing the actions that will apply to each player."]]
   (map (fn [[etype m] [_ prev-m]]
          [etype
           (case etype
             :user (gen-user world m)
             :assistant (let [world world
                              #_(reduce
                                     (fn [world [cid {:keys [loc]}]]
                                       (assoc-in world [:characters cid :loc] loc))
                                     world
                                     prev-m)]
                          (gen-assistant world m)))])
        (:dm-history world)
        (cons nil (:dm-history world)))
   [[:user (gen-user world user-data)]]))
