(ns us.chouser.gryphonport.main
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [us.chouser.gryphonport.location-graph :as loc]
            [us.chouser.gryphonport.util :as util])
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
          #{:s003 :d003}
          #{:s013 :s009}
          #{:s013 :r016}
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

(alter-var-root #'seed-graph assoc-in [:nodes :r013 :description]
                "You find yourself standing in the Entrance Hall of Gryphonport's Town Hall. The room is dimly lit with flickering torches mounted on the walls, creating a warm and inviting atmosphere. The air is filled with the scent of old books and polished wood, suggesting that the building has a long history.

In the center of the room stands a grand statue of a majestic griffin, its wings outstretched as if about to take flight. The statue is made of polished wood and stands atop a pedestal decorated with intricate carvings. It immediately catches your eye as you take in the details of the room.

Looking around, you notice several doors leading to different areas of the building. One door to the Guard Room and another door that leads to the Archives. A grand staircase leads up to the Council Chambers, where the town's leaders meet to make important decisions.

Another striking feature of the Entrance Hall is the ceiling above you, which is decorated with a large mural. It depicts the early settlers of Gryphon fighting against ferocious beasts that once roamed the land. The mural is incredibly detailed and tells the story of the town's heroic origins.")

(alter-var-root #'seed-graph assoc-in [:nodes :r016 :description]
                "As you enter the Guard Room, you find yourself in a small, windowless chamber. The room is dimly lit by torches mounted on the walls, casting flickering shadows across the floor.

The space is dominated by a large, sturdy oak table that takes up most of the room. The table is surrounded by several wooden chairs. In one corner of the room, you spot several weapons hung on the wall, including swords, spears, and shields.

Looking around, you notice that the walls are decorated with several paintings depicting scenes of battle and conquest. The images are vivid and detailed, showing warriors in armor fighting against mythical beasts and other enemies. It's clear that this room was meant to inspire bravery and determination in those who used it.

You also notice several doors, one leading to the Keep Bridge, one to Jail, and one to the Entrance Hall with it's grand staircase.")

(alter-var-root #'seed-graph assoc-in [:nodes :s003 :description]
                "As you step onto Gryphonport's Main Street, you are immediately struck by the lively atmosphere. People bustle about, haggling with vendors over wares and exchanging news and gossip with their neighbors. The buildings lining the street are a mix of homes, shops, and other establishments, creating a vibrant atmosphere.

From Main Street, you can access several different parts of town. South Street is a smaller street that branches off. You can hear the ringing of hammers on metal at Wilhelm's Forge nearby. A sign depicting three ducks hangs outside the door of the Odd Duck, from which spills the sounds of laughter and music. One end of Main Street connects to the Farmstead, and at the other is the impressive but rustic Town Hall.")


(defn read-world []
  (->> "us/chouser/world.edn" io/resource io/reader PushbackReader.
       read))

(defonce *world
  (atom (read-world)))

(defn write-world []
  (with-open [w (io/writer (io/resource "us/chouser/world.edn"))]
    (binding [*out* w]
      (prn @*world))))

(defn populate [id]
  (let [graph @*world]
    (->> (util/chat {:msgs (loc/prompt-msgs graph id)})
         util/content
         loc/parse-content
         (loc/id-content graph id)
         (swap! *world loc/merge-subgraph)))
  (write-world)
  (let [dot-str (loc/dot @*world)]
    (spit "world.dot" dot-str)
    (sh "dot" "-Tsvg" "-o" "world.svg" :in dot-str)))


(defn _comment []
  (reset! *world seed-graph)
  (reset! *world (read-world))
  (write-world)
  (spit "world.dot" (loc/dot @*world))
  (sh "dot" "-Tsvg" "-o" "world.svg" :in (loc/dot @*world))

  (def resp (util/chat {:msgs (loc/prompt-msgs seed-graph :s007)}))
  (println (util/content resp))

  (populate :s005)

  (util/pprint-msgs (loc/prompt-msgs seed-graph :s005))

  )
