(ns us.chouser.gryphonport.main
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [us.chouser.gryphonport.location-graph :as loc]
            [us.chouser.gryphonport.location-descriptions :as desc]
            [us.chouser.gryphonport.dm :as dm]
            [us.chouser.gryphonport.util :as util]
            [clojure.data.json :as json])
  (:import (java.io PushbackReader)))

(set! *warn-on-reflection* true)

(def seed-graph
  {:nodes
   {:a001 {:name "Gryphon"              :kind "region" :parent nil :children? true}
    :d002 {:name "Gryphonport"          :kind "town" :parent :a001 :children? true}
    :s003 {:name "Main Street"          :kind "street" :parent :d002}
    :s004 {:name "Town Hall"            :kind "building" :parent :d002 :children? true}
    :s005 {:name "Wilhelm's Forge"      :kind "building" :parent :d002 :children? true}
    :s006 {:name "Odd Duck"             :kind "tavern" :parent :d002 :children? true}
    :s007 {:name "Temple of the Divine" :kind "building" :parent :d002 :children? true}
    :s008 {:name "Dockside"             :kind "area" :parent :d002}
    :s009 {:name "Keep"                 :kind "building" :parent :d002 :children? true}
    :s010 {:name "Alchemist's Shop"     :kind "building" :parent :d002 :children? true}
    :s011 {:name "Farmstead"            :kind "area" :parent :d002}
    :s012 {:name "Graveyard"            :kind "area" :parent :d002}
    :s013 {:name "Keep Bridge"          :kind "bridge" :parent :d002}
    :s014 {:name "South Street"         :kind "street" :parent :d002}
    :s015 {:name "Alchemist's Alley"    :kind "back alley" :parent :d002}
    :r013 {:name "Entrance Hall"        :kind "room" :parent :s004}
    :r014 {:name "Council Chamber"      :kind "room" :parent :s004}
    :r015 {:name "Archives"             :kind "room" :parent :s004}
    :r016 {:name "Guard Room"           :kind "room" :parent :s004}
    :r017 {:name "Treasury"             :kind "room" :parent :s004}
    :r018 {:name "Jail"                 :kind "room" :parent :s004}
    :d100 {:name "Blackwood Station"    :kind "outpost"  :parent :a001 :children? true}
    :d102 {:name "Blackwood Trail"      :kind "road"     :parent :a001}
    :s102 {:name "Stable Yard"          :kind "area"     :parent :d100}
    :s103 {:name "Watchtower"           :kind "building" :parent :d100 :children? true}
    :r104 {:name "Kitchen"              :kind "room"     :parent :s103}
    :r105 {:name "Bunkroom"             :kind "room"     :parent :s103}
    :r106 {:name "Watchroom"            :kind "room"     :parent :s103}
    :d103 {:name "Brookside Road"       :kind "road"     :parent :a001}
    :d104 {:name "Brookside"            :kind "town"     :parent :a001 :children? true}}
   :adj #{#{:r013 :r014}
          #{:r013 :r015}
          #{:r013 :r016}
          #{:r013 :s003}
          #{:r015 :r014}
          #{:r017 :r016}
          #{:r018 :r016}
          #{:s003 :s004}
          #{:s003 :s006}
          #{:s005 :s003}
          #{:s005 :s015}
          #{:s015 :s010}
          #{:s007 :s012}
          #{:s008 :s006}
          #{:s011 :s003}
          #{:d002 :d102}
          #{:s013 :s009}
          #{:s013 :r016}
          #{:s013 :s004}
          #{:s014 :s003}
          #{:s014 :s010}
          #{:s014 :s007}
          #{:s014 :d102}

          #{:s102 :s103}
          #{:s102 :r104}
          #{:r104 :r105}
          #{:r104 :r106}
          #{:d102 :s102}
          #{:d100 :d102}
          #{:d102 :d103}
          #{:d103 :d104}}})

(def seed-descriptions
  {:r013
   "You find yourself standing in the Entrance Hall of Gryphonport's Town Hall. The room is dimly lit with flickering torches mounted on the walls, creating a warm and inviting atmosphere. The air is filled with the scent of old books and polished wood, suggesting that the building has a long history.

In the center of the room stands a grand statue of a majestic griffin, its wings outstretched as if about to take flight. The statue is made of polished wood and stands atop a pedestal decorated with intricate carvings. It immediately catches your eye as you take in the details of the room.

Looking around, you notice several doors leading to different areas of the building. One door to the Guard Room and another door that leads to the Archives. A grand staircase leads up to the Council Chambers, where the town's leaders meet to make important decisions.

Another striking feature of the Entrance Hall is the ceiling above you, which is decorated with a large mural. It depicts the early settlers of Gryphon fighting against ferocious beasts that once roamed the land. The mural is incredibly detailed and tells the story of the town's heroic origins.

The front doors, which reach nearly to the vaulted ceiling, lead out to Main Street."

   :r016
   "As you enter the Guard Room, you find yourself in a small, windowless chamber. The room is dimly lit by torches mounted on the walls, casting flickering shadows across the floor.

The space is dominated by a large, sturdy oak table that takes up most of the room. The table is surrounded by several wooden chairs. In one corner of the room, you spot several weapons hung on the wall, including swords, spears, and shields.

Looking around, you notice that the walls are decorated with several paintings depicting scenes of battle and conquest. The images are vivid and detailed, showing warriors in armor fighting against mythical beasts and other enemies. It's clear that this room was meant to inspire bravery and determination in those who used it.

You also notice several doors, one leading to the Keep Bridge, one to Jail, and one to the Entrance Hall with it's grand staircase."

   :s003
   "As you step onto Gryphonport's Main Street, you are immediately struck by the lively atmosphere. People bustle about, haggling with vendors over wares and exchanging news and gossip with their neighbors. The buildings lining the street are a mix of homes, shops, and other establishments, creating a vibrant atmosphere.

From Main Street, you can access several different parts of town. South Street is a smaller street that branches off. You can hear the ringing of hammers on metal at Wilhelm's Forge nearby. A sign depicting three ducks hangs outside the door of the Odd Duck, from which spills the sounds of laughter and music. One end of Main Street connects to the Farmstead, and at the other is the impressive but rustic Town Hall."

   :a001
   "Gryphon is a vast, rugged region located in the northern part of the continent. It is known for its breathtaking natural beauty, with jagged mountain peaks, deep forests, and winding rivers that cut through the landscape. The region is also home to a diverse array of wildlife, including wolves, bears, deer, and many species of birds.

Despite its natural beauty, Gryphon is a challenging place to live. The rugged terrain and harsh climate make it difficult to grow crops, and the inhabitants must be resourceful and self-sufficient to survive. Many people in Gryphon rely on hunting, fishing, and trapping to make a living.

The people of Gryphon are known for their hardiness and resilience, as well as their fierce independence. They are a proud people who value self-sufficiency and individual freedom, and they are fiercely protective of their land and way of life.

Despite its challenges, Gryphon is a popular destination for adventurers and explorers. The region is home to many hidden treasures, including ancient ruins, lost cities, and hidden valleys that are said to be home to magical creatures and powerful artifacts. Many adventurers come to Gryphon in search of fame, fortune, and adventure, and while many are never heard from again, those who succeed in their quests often become legends in their own right."

   :d002
   "Gryphonport is a bustling town located in the heart of Gryphon, near the famous Blackwood Trail road. The town is situated at the foot of a towering mountain range and surrounded by dense forests, giving it a sense of isolation and ruggedness.

The town is centered around Main Street, a tidy thoroughfare lined with shops, inns, and other establishments."

   :s005
   "Wilhelm's Forge is a large, imposing building located in Gryphonport, near Main Street and Alchemist's Alley. The building is made of sturdy stone and is easily recognizable by the large sign hanging above the entrance, depicting a hammer and anvil."})

(def characters
  {:p1 {:loc :r013}})

(defn merge-descriptions [graph description-map]
  (reduce (fn [m [k d]]
            (assoc-in m [:nodes k :description] d))
          graph
          description-map))

(defn describe-down-to [world loc]
  (reduce (fn [world loc]
            (if-let [desc (:description (loc/node world loc))]
              world
              (do
                (prn :describe loc)
                (merge-descriptions
                 world
                 {loc (util/content
                       (util/chat {:msgs (desc/prompt-msgs world loc)}))}))))
          world
          (concat (loc/path-from-root world loc)
                  [loc])))

(defn move-character [world cid loc]
  (let [n (loc/node world loc)
        orig-loc (get-in world [:characters cid :loc])]
    (if (not (:children? n))
      (-> world
          (describe-down-to loc)
          (assoc-in [:characters cid :loc] loc))
      (let [world (if (seq (loc/get-parts world loc))
                    world
                    (do
                      (prn :populate loc)
                      (->> (util/chat {:msgs (loc/prompt-msgs world loc)})
                           util/content
                           loc/parse-content
                           (loc/id-content world loc)
                           (loc/merge-subgraph world))))]
        (recur world cid (loc/find-gateway world orig-loc loc))))))

(defn read-world []
  (->> "us/chouser/world.edn" io/resource io/reader PushbackReader.
       read))

(defonce *world
  (atom (read-world)))

(defn write-world []
  (let [dot-str (loc/dot @*world)]
    (spit "world.dot" dot-str)
    (sh "dot" "-Tsvg" "-o" "world.svg" :in dot-str))
  (with-open [w (io/writer (io/resource "us/chouser/world.edn"))]
    (binding [*out* w]
      (prn @*world))))

(defn info []
  (let [char-id :p1
        world @*world
        loc-id (get-in world [:characters char-id :loc])]
    (println (get-in world [:nodes loc-id :description]))))

(defn go [loc]
  (let [char-id :p1
        world (move-character @*world char-id loc)]
    (reset! *world world)
    (write-world)
    (info)))

(defn talk [world txt]
  (let [world (update-in world [:characters :barman :chat]
                         (fnil conj []) [:user txt])
        msgs (concat
              [[:system "The assistant is the barman."]
               [:user ["Pretend to be the barman at the Odd Duck for this entire conversation."
                       #_(get-in world [:nodes
                                      (get-in world [:characters :p1 :loc])
                                      :description])]]
               [:assistant ["Ok!"]]]
              (get-in world [:characters :barman :chat]))
        content (->> (util/chat {:msgs msgs}) util/content)

        world (update-in world [:characters :barman :chat]
                         conj [:assistant content])]
    (println "Barman says:" content)
    world))

(defn cmd [text]
  (let [char-id :p1
        world @*world
        dm-user-data (dm/add-instruction-locs world {char-id text})
        dm-cmds (->> (util/chat {:msgs (dm/prompt-msgs world dm-user-data)})
                     util/content
                     dm/parse-content
                     (dm/id-content world))
        ;;_ (clojure.pprint/pprint dm-cmds)
        world (update world :dm-history conj
                      [:user dm-user-data]
                      [:assistant dm-cmds])
        world (reduce (fn [world [cid m]]
                        (when-let [r (:reply m)]
                          (println r)
                          (println))
                        (let [world (if-let [go-loc (:go m)]
                                      (move-character world cid go-loc)
                                      world)
                              world (if-let [txt (:say m)]
                                      (do
                                        (println "(You say:" txt ")")
                                        (talk world txt))
                                      world)]
                          world))
                      world
                      dm-cmds)]
    (reset! *world world)
    (write-world)
    #_(info)))

#_
(defn _comment []
  (reset! *world seed-graph)
  (swap! *world merge-descriptions seed-descriptions)
  (swap! *world assoc :characters characters)
  (swap! *world assoc :dm-history dm/example)

  (swap! *world assoc-in [:characters :p1 :loc] :pq541)

  (swap! *world assoc-in [:characters :barman :chat] [])

  (def resp (util/chat {:msgs (dm/prompt-msgs @*world {:p1 "say goodbye and go to main street"})}))

  (util/pprint-msgs (dm/prompt-msgs @*world (dm/add-instruction-locs @*world {:p1 "say goodbye and go to main street"})))

  (reset! *world (read-world))
  (write-world)
  (spit "world.dot" (loc/dot @*world))
  (sh "dot" "-Tsvg" "-o" "world.svg" :in (loc/dot @*world))

  (def resp (util/chat {:msgs (desc/prompt-msgs @*world :ev370)}))
  (println (util/content resp))

  (swap! *world update-in [:nodes :d102] dissoc :description)

  (get-in @*world [:dm-history])

  (populate :s005)
  (describe :oo761)

  (util/pprint-msgs (loc/prompt-msgs seed-graph :s005))

  (desc/gen-user @*world :s005)

  )
