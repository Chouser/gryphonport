(ns us.chouser.gryphonport.main
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [us.chouser.gryphonport.location-descriptions :as desc]
            [us.chouser.gryphonport.location-graph :as loc]
            [us.chouser.gryphonport.npc :as npc]
            [us.chouser.gryphonport.util :as util])
  (:import (java.io PushbackReader)))

(def *world (util/make-resource-obj atom "resources/us/chouser/world.edn" {}))

(add-watch *world ::write-dot
           (fn [_ _ _ new-value]
             (let [dot-str (loc/dot new-value)]
               (spit "world.dot" dot-str)
               (future (sh "dot" "-Tsvg" "-o" "world.svg" :in dot-str)))))

(defn info []
  (let [char-id :p1
        world @*world
        loc-id (get-in world [:characters char-id :loc])]
    (println (get-in world [:nodes loc-id :description]))))

#_
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
                                      world)]
                          world))
                      world
                      dm-cmds)]
    (reset! *world world)
    #_(info)))

(defn go []
  (let [w @*world
        actor-id (-> w :next-turn first)
        _ (prn :actor-id actor-id)
        instruction (-> {:msgs (npc/prompt-actor w actor-id)}
                        util/chat
                        util/content)
        _ (println instruction)]
    (try
      (let [parsed-instruction (npc/parse-actor-content w
                                                        (-> w :actors actor-id :loc)
                                                        instruction)
            w (npc/apply-actor-content w actor-id parsed-instruction) ;; TODO move this into apply-narrator-content
            narr-instruction (-> parsed-instruction
                                 (dissoc :reason)
                                 (assoc :src actor-id))
            narration (-> {:msgs (npc/prompt-narrator w narr-instruction)}
                          util/chat
                          util/content
                          (npc/parse-narrator-content)
                          (merge narr-instruction))]
        (clojure.pprint/pprint {:narr narration})
        (reset! *world (npc/apply-narrator-content w narration))
        (println (or (:response3 narration)
                     (:response3-going narration)
                     (str "Do over: " (:error narration)))))
      (catch Exception ex
        (if-let [amem (:actor-mem (ex-data ex))]
          (do
            (reset! *world (update-in w [:actors actor-id :mem] conj amem))
            (prn amem))
          (throw ex))))))

#_
(defn _comment []

  (util/pprint-msgs (npc/prompt-actor @*world :cori))

  (npc/parse-actor-content @*world
                           :s003
                           "travel-to: Odd Duck\n\nreason: I bet there are some adventerous people in the tavern.")

  (def resp
    (->
     (npc/prompt-narrator @*world
                          {:loc :ri662
                           :src :cori
                           :travel-to :s003,
                           ;;:say "yow",
                           :reason "I bet there are some adventerous people in the tavern."})
     (doto util/pprint-msgs)
     {}
     #_util/chatm))

  (npc/parse-narrator-content
   "The instruction makes sense. Here are the three requested formats:\n\nThird person coming: Cori enters Main Street.\nThird person going: Cori walks out of the Odd Duck and onto Main Street.\nSecond person: You walk out of the Odd Duck and onto Main Street.")

  (npc/parse-narrator-content
   "The instruction makes sense. Here are the three requested formats:\n\nThird person: Cori say hi\n\nSecond person: You say hi")


  (npc/parse-narrator-content
   "I'm sorry, I'm not sure what you meant by \"yow\". Could you please clarify or provide more context?")

  (println (util/content resp))

  (npc/apply-narrator-content
   @*world
   {:loc :ri662
    :travel-to :s003
    :src :cori
    :response2 "You stroll to the Bar Room"
    :response3-going "Cori strolls to the Bar Room"
    :response3-coming "Cori strolls in from the Bar Room"})

  (reset! *world seed-graph)
  (swap! *world merge-descriptions seed-descriptions)
  (swap! *world assoc :characters characters)

  (swap! *world assoc-in [:characters :p1 :loc] :pq541)

  (swap! *world assoc-in [:characters :barman :chat] [])

  (spit "world.dot" (loc/dot @*world))
  (sh "dot" "-Tsvg" "-o" "world.svg" :in (loc/dot @*world))

  (def resp (util/chat {:msgs (desc/prompt-msgs @*world :ev370)}))
  (println (util/content resp))

  (swap! *world update-in [:nodes :d102] dissoc :description)

  (populate :s005)
  (describe :oo761)

  (util/pprint-msgs (loc/prompt-msgs seed-graph :s005))

  (desc/gen-user @*world :s005)

  )
