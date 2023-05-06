(ns us.chouser.gryphonport.npc
  (:require [us.chouser.gryphonport.util :as util]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

(def *state (util/make-resource-obj atom "resources/us/chouser/npc-state.edn" state))

(defn prompt-actor [actor]
  (into [[:system (util/fstr ["You are controlling " (:name actor) " in this scene."])]]
        (->> (cons {:text (:self-bio actor)} (:mem actor))
             (partition-by #(contains? % :text))
             (map (fn [mems]
                    (if (-> mems first :text)
                      [:user (util/fstr (interpose "\n\n" (map :text mems))
                                        "\n\nWhat should you do, and why? Choose between the commands `say` and `travel to`.")]
                      [:assistant (->> mems
                                       (map (fn [mem]
                                              (cond
                                                (:say mem) ["say: " (:say mem)
                                                            "\n\nreason: " (:reason mem)])))
                                       (interpose "\n\n")
                                       util/fstr)]))))))

(comment

  (util/pprint-msgs
   (prompt-actor (get-in @*state [:actors :rafe])))

  )

(defn prompt-narrator [w actor-id actor-instruction]
  (let [q (->> w :narration
               (mapcat (fn [{:keys [src say error response2 response3]}]
                         (let [nom (get-in w [:actors src :name])]
                           [[:user nom " wants to say: " say] ;; TODO: support travel to etc.
                            (if-not error
                              [:assistant
                               "Third person: " response3
                               "\n\nSecond person (with " nom " as 'you'): " response2]
                              [:assistant error])]))))
        nom (get-in w [:actors actor-id :name])]
    (concat
     [[:system "You are narrating a story, taking instructions for the characters involved."]
      [:user "The Bar Room is the heart of Gryphonport's popular tavern, Odd Duck. As you step inside, you're greeted by the warm light of flickering candles and the sounds of lively conversation and music. The room is cozy and inviting, with a rustic decor that adds to its charm.

The space is dominated by a long wooden bar, behind which stands the friendly bartender, ready to pour you a cold drink or mix up a signature cocktail. The bar is lined with a variety of bottles, some of which you recognize and others that are unfamiliar to you.

You can see Cornelia Finch, A young woman with short, tousled chestnut hair that frames her oval-shaped face. She has expressive hazel eyes that convey her passion and determination. She carries herself with poise and grace. Her fingers are long and slender, and she is wearing simple yet elegant clothing that allows her to move freely.

You can see Rafe Hunter, a young man with a spiky shock of dark hair and wide, dark eyes. He's wearing the thick leather clothes of a woodsman, but they look conspicuously clean as if they're brand-new.\n\n"
       ;; Append the first :user entry to this header text:
       (-> q first rest)]]
     (rest q)
     [#_
      [[:example-user "Instructions for Bob: say: Jill tell me a story"]
       [:example-assistant "Third person: Bob thinks for a moment. \"Jill, tell me a story,\" he says.\n\nSecond person (with Bob as 'you'): You think for a moment. \"Jill, tell me a story,\" you say."]
       [:example-user "Instructions for George: turn into a pumpkin"]
       [:example-assistant "Error: You can't do that. Try something else."]
       [:example-user "Instructions for Jill: tell a story"]
       [:example-assistant "Error: That is too vague. Please give exactly the words you want to say."]]

      [:user nom " wants to say: " (:say actor-instruction)
       "\n\nDescribe the actions " nom " will take (in third-person and second-person form). If the instructions are impossible or unclear, return 'Error' and ask for clarification instead."]])))

(defn parse-actor-content [s]
  (->> (re-seq #"(?m)^(say|travel to|reason): (.*)" s)
       (map (fn [[_ k v]]
              [(keyword k) v]))
       (into {})))

(defn apply-actor-content [w actor-id parsed-instruction]
  (update-in w [:actors actor-id :mem] (fnil conj []) parsed-instruction))

(defn parse-narrator-content [s src parsed-instruction]
  (let [[_ r3 r2] (re-find #"(?ms)^Third person: (.*?)\s+^Second person(?:[^:]*): (.*)" s)
        error (when-not r2 s)]
    (cond-> {:src src, :say (:say parsed-instruction)} ;; TODO: support other actions
      r2 (assoc :response3 r3 :response2 r2)
      error (assoc :error error))))

(defn apply-narrator-content [w {:keys [src response2 response3 error] :as m}]
  (let [w (update w :narration (fnil conj []) m)]

    (if error
      (update-in w [:actors src :mem] (fnil conj []) {:text (util/fstr "Error: " error)})

      (let [w (update w :next-turn (fn [ids] (conj (vec (rest ids))
                                                   (first ids))))]
        ;; For every actor (todo: in the room)
        (reduce (fn [w actor-id]
                  (update-in w [:actors actor-id :mem] (fnil conj [])
                             {:text
                              (if (= actor-id src)
                                response2
                                response3)}))
                w
                (-> w :actors keys))))))

(defn go []
  (let [state @*state
        actor-id (-> state :next-turn first)
        _ (prn :actor-id actor-id)
        instruction (-> {:msgs (prompt-actor (-> state :actors actor-id))}
                             util/chat
                             util/content)
        _ (println instruction)
        parsed-instruction (parse-actor-content instruction)
        state (apply-actor-content state actor-id parsed-instruction)
        narration (-> {:msgs (prompt-narrator
                              state actor-id parsed-instruction)} ;; todo use map from apply below?
                      util/chat
                      util/content
                      (parse-narrator-content actor-id parsed-instruction))]
    (reset! *state (apply-narrator-content state narration))
    (println (or (:response3 narration) (str "Do over: " (:error narration))))))

(comment

  (apply-actor-content @*state :rafe
                       "say did you know I once defeated a whole band of theives?\n\nIf I impress her, maybe she'll keep talking to me.")

  ;; TODO work this prompt! Get narrotor to refuse.
  (util/pprint-msgs (prompt-narrator @*state
                                     :rafe
                                     (parse-actor-content
                                      "say: Well, it all started when a wealthy merchant hired me to find a lost artifact...\n\nreason: I'm happy to share the details of my adventure, and it might be interesting to her. However, I won't go into too much detail, just in case she's not trustworthy.")))

  (println (util/content r))

  (def r (util/chat {:msgs (prompt-actor (-> @*state :actors :rafe))}))

  (apply-actor-content @*state :cori)

  (def r (util/chat {:msgs cori}))

  (def r (util/chat {:msgs rafe}))

  (def r (util/chat {:msgs narrator}))

  (parse-narrator-content (second (last narrator)))

  (def nav
    (-> "log/2023-05-04T03:26:33Z.edn"
        (util/read-file {})
        (update :response #(assoc % :body-map (clojure.data.json/read-str (:body %) :key-fn keyword)))
        :response
        (util/content)))

  )
