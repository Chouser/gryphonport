(ns us.chouser.gryphonport.npc
  (:require [us.chouser.gryphonport.util :as util]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

(def narrator
  [[:system "You are narrating a story, taking instructions from the characters involved."]
   [:user "The Bar Room is the heart of Gryphonport's popular tavern, Odd Duck. As you step inside, you're greeted by the warm light of flickering candles and the sounds of lively conversation and music. The room is cozy and inviting, with a rustic decor that adds to its charm.

The space is dominated by a long wooden bar, behind which stands the friendly bartender, ready to pour you a cold drink or mix up a signature cocktail. The bar is lined with a variety of bottles, some of which you recognize and others that are unfamiliar to you.

You can see Cornelia Finch, A young woman with short, tousled chestnut hair that frames her oval-shaped face. She has expressive hazel eyes that convey her passion and determination. She carries herself with poise and grace. Her fingers are long and slender, and she is wearing simple yet elegant clothing that allows her to move freely.

You can see Rafe Hunter, a young man with a spiky shock of dark hair and wide, dark eyes. He's wearing the thick leather clothes of a woodsman, but they look conspicuously clean as if they're brand-new.

Instructions from Cori: join rafe"]
   [:assistant "Second person: Your eyes sparkle mischievously as you say, \"Is this seat taken?\"
Third person: Cori's eyes sparkle mischievously as she says, \"Is this seat taken?\""]
   [:user "Instructions from Rafe: invite her to sit"]
   [:assistant "Second person: \"Not at all,\" you reply, gesturing to the empty chair opposite you. \"Please, sit down.\"
Third person: \"Not at all,\" Rafe replies, gesturing to the empty chair opposite him. \"Please, sit down.\""]
   [:user "Instructions from Cori: say what brings you to here?"]
   [:assistant "Second person: You take a seat, and before the silence gets awkward you ask, \"So, what brings you to Gryphenport?\"
Third person: She takes a seat, and before the silence gets awkward she asks, \"So, what brings you to Gryphenport?\""]
   [:user "Instructions from Rafe: say I'm here on business"]
   [:assistant "Second person: \"I'm here on business,\" you reply vaguely.
Third person: \"I'm here on business,\" he replies vaguely."]
   [:user "Instructions from Cori: ask Rafe what kind of business he is in?"]
   [:assistant "Second person: \"Business, eh? What kind of business?\" you press.
Third person: \"Business, eh? What kind of business?\" she presses."]
   [:user "Instructions from Rafe: be evasive"]
   [:assistant "Second person: \"Just some freelance work,\" you say with a shrug. \"Nothing exciting, really.\"
Third person: \"Just some freelance work,\" Rafe says with a shrug. \"Nothing exciting, really.\""]
   [:user "Instructions from Cori: ask Rafe if he has any interesting stories to share?"]
   [:assistant "Second person: \"Well, even freelance work can be interesting,\" you say with a smile. \"Do you have any interesting stories to share?\"
Third person: \"Well, even freelance work can be interesting,\" Cori says with a smile. \"Do you have any interesting stories to share?\""]])

(def rafe
  [[:system "You are controlling Rafe in this scene."]
   [:user "You are a young man, son of successful merchant John Foster, who long ago became convinced the life of a merchant was too boring to pursue. Though it caused a falling-out with your father, you left home and your father's dreams for you, to seek a living adventuring in the wide world. You're better than average at knife-fighting and navigating, but the skill that sets you apart is your hard-nosed, realistic view of the world and your ability to unhesitatingly take the necessary action. Your name is Rafe Foster, but in order to avoid association with your rich father, you usually go by Rafe Hunter.

The Bar Room is the heart of Gryphonport's popular tavern, Odd Duck. As you step inside, you're greeted by the warm light of flickering candles and the sounds of lively conversation and music. The room is cozy and inviting, with a rustic decor that adds to its charm.

The space is dominated by a long wooden bar, behind which stands the friendly bartender, ready to pour you a cold drink or mix up a signature cocktail. The bar is lined with a variety of bottles, some of which you recognize and others that are unfamiliar to you.

You can see Cornelia Finch, A young woman with short, tousled chestnut hair that frames her oval-shaped face. She has expressive hazel eyes that convey her passion and determination. She carries herself with poise and grace. Her fingers are long and slender, and she is wearing simple yet elegant clothing that allows her to move freely.

Cori's eyes sparkle mischievously as she says, \"Is this seat taken?\"

What should you do, and why?"]

   [:assistant "invite her to sit

She doesn't look like she has much money, but I'm always happy to talk to a pretty girl."]

   [:user "\"Not at all,\" you reply, gesturing to the empty chair opposite you. \"Please, sit down.\"

She takes a seat, and before the silence gets awkward she asks, \"So, what brings you to Gryphenport?\"

What should you do, and why?"]
   [:assistant "say I'm here on business

I'm a little suspicious about how forward she's being. Perhaps she wants something, so I don't want to give too much away."]
   [:user "\"I'm here on business,\" you reply vaguely.

\"Business, eh? What kind of business?\" she presses.

What should you do, and why?"]

   [:assistant "be evasive

I don't want to reveal too much about my business or myself. It's best to keep things vague for now."]
   [:user "\"Just some freelance work,\" you say with a shrug. \"Nothing exciting, really.\"

\"Well, even freelance work can be interesting,\" Cori says with a smile. \"Do you have any interesting stories to share?\"

What should you do, and why?"]])

(def cori
  [[:system "You are controlling Cori in this scene."]
   [:user "You are a young woman named Cornelia Finch who grew up in a poor family in a small fishing village. Your friends call you Cori. As a child, you discovered your love for music and would sing and play your lute whenever you had a chance. Your parents disapproved, but you loved it so much you continued to pursue your passion and eventually became a minstrel.

Your dream is to compose a musical masterpiece that will be remembered for generations to come. You often act impulsively and also struggle with the drawbacks of perfectionism.

The Bar Room is the heart of Gryphonport's popular tavern, Odd Duck. As you step inside, you're greeted by the warm light of flickering candles and the sounds of lively conversation and music. The room is cozy and inviting, with a rustic decor that adds to its charm.

The space is dominated by a long wooden bar, behind which stands the friendly bartender, ready to pour you a cold drink or mix up a signature cocktail. The bar is lined with a variety of bottles, some of which you recognize and others that are unfamiliar to you.

You can see Rafe Hunter, a young man with a spiky shock of dark hair and wide, dark eyes. He's wearing the thick leather clothes of a woodsman, but they look conspicuously clean as if they're brand-new.

What should you do, and why?"]

   [:assistant "join rafe

I need to find an interesting character to write a song abount. Maybe Rafe is interesting."]

   [:user "Your eyes sparkle mischievously as you say, \"Is this seat taken?\"

\"Not at all,\" Rafe replies, gesturing to the empty chair opposite him. \"Please, sit down.\"

What should you do, and why?"]

   [:assistant "say what brings you to here?

I want to find out if Rafe is interesting."]

   [:user "You take a seat, and before the silence gets awkward you ask, \"So, what brings you to Gryphenport?\"

\"I'm here on business,\" Rafe replies vaguely.

What should you do, and why?"]

   [:assistant "ask Rafe what kind of business he is in?

I want to know more about Rafe's business and find out if it's interesting enough to write a song about."]

   [:user "\"Business, eh? What kind of business?\" you press.

\"Just some freelance work,\" Rafe says with a shrug. \"Nothing exciting, really.\"

What should you do, and why?"]])

;; narrator has interpreted instructions from cori.  It's rafe's turn
(def state
  {:next-turn [:rafe :cori],
   :narration
   [{:src :cori,
     :instruction "join rafe",
     :response2
     "Your eyes sparkle mischievously as you say, \"Is this seat taken?\"",
     :response3
     "Cori's eyes sparkle mischievously as she says, \"Is this seat taken?\""}
    {:src :rafe,
     :instruction "invite her to sit",
     :response2
     "\"Not at all,\" you reply, gesturing to the empty chair opposite you. \"Please, sit down.\"",
     :response3
     "\"Not at all,\" Rafe replies, gesturing to the empty chair opposite him. \"Please, sit down.\""}
    {:src :cori,
     :instruction "say what brings you to here?",
     :response2
     "You take a seat, and before the silence gets awkward you ask, \"So, what brings you to Gryphenport?\"",
     :response3
     "Cori takes a seat, and before the silence gets awkward she asks, \"So, what brings you to Gryphenport?\""}
    {:src :rafe,
     :instruction "say I'm here on business",
     :response2 "\"I'm here on business,\" you reply vaguely.",
     :response3 "\"I'm here on business,\" he replies vaguely."}
    {:src :cori,
     :instruction "ask Rafe what kind of business he is in?",
     :response2 "\"Business, eh? What kind of business?\" you press.",
     :response3 "\"Business, eh? What kind of business?\" Cori presses."}
    {:src :rafe,
     :instruction "be evasive",
     :response2
     "\"Just some freelance work,\" you say with a shrug. \"Nothing exciting, really.\"",
     :response3
     "\"Just some freelance work,\" Rafe says with a shrug. \"Nothing exciting, really.\""}
    {:src :cori,
     :instruction "ask if he has any interesting stories to share?",
     :response2
     "\"Well, even freelance work can be interesting,\" you say to Rafe with a smile. \"Do you have any interesting stories to share?\"",
     :response3
     "\"Well, even freelance work can be interesting,\" Cori says to Rafe with a smile. \"Do you have any interesting stories to share?\""}
    {:src :rafe,
     :instruction "share a harmless, entertaining story",
     :error "Please give more specific instructions."}],

   :actors
   {:rafe
    {:name "Rafe Hunter",
     :self-bio
     "You are a young man, son of successful merchant John Foster, who long ago became convinced the life of a merchant was too boring to pursue. Though it caused a falling-out with your father, you left home and your father's dreams for you, to seek a living adventuring in the wide world. You're better than average at knife-fighting and navigating, but the skill that sets you apart is your hard-nosed, realistic view of the world and your ability to unhesitatingly take the necessary action. Your name is Rafe Foster, but in order to avoid association with your rich father, you usually go by Rafe Hunter.",
     :mem
     [{:text
       "The Bar Room is the heart of Gryphonport's popular tavern, Odd Duck. As you step inside, you're greeted by the warm light of flickering candles and the sounds of lively conversation and music. The room is cozy and inviting, with a rustic decor that adds to its charm.\n\nThe space is dominated by a long wooden bar, behind which stands the friendly bartender, ready to pour you a cold drink or mix up a signature cocktail. The bar is lined with a variety of bottles, some of which you recognize and others that are unfamiliar to you.\n\nYou can see Cornelia Finch, A young woman with short, tousled chestnut hair that frames her oval-shaped face. She has expressive hazel eyes that convey her passion and determination. She carries herself with poise and grace. Her fingers are long and slender, and she is wearing simple yet elegant clothing that allows her to move freely."}
      {:text
       "Cori's eyes sparkle mischievously as she says, \"Is this seat taken?\""}
      {:instruct
       "invite her to sit\n\nShe doesn't look like she has much money, but I'm always happy to talk to a pretty girl."}
      {:text
       "\"Not at all,\" you reply, gesturing to the empty chair opposite you. \"Please, sit down.\""}
      {:text
       "Cori takes a seat, and before the silence gets awkward she asks, \"So, what brings you to Gryphenport?\""}
      {:instruct
       "say I'm here on business\n\nI'm a little suspicious about how forward she's being. Perhaps she wants something, so I don't want to give too much away."}
      {:text "\"I'm here on business,\" you reply vaguely."}
      {:text "\"Business, eh? What kind of business?\" Cori presses."}
      {:instruct
       "be evasive\n\nI don't want to reveal too much about my business or myself. It's best to keep things vague for now."}
      {:text
       "\"Just some freelance work,\" you say with a shrug. \"Nothing exciting, really.\""}
      {:text
       "\"Well, even freelance work can be interesting,\" Cori says to Rafe with a smile. \"Do you have any interesting stories to share?\""}
      {:instruct
       "share a harmless, entertaining story\n\nI don't see any harm in sharing a fun story, and it could help establish a rapport with Cori."}
      {:text "Your instructions are too vague. What story?"}
      {:instruct "say did you know I once defeated a whole band of theives?\n\nIf I impress her, maybe she'll keep talking to me."}]},
    :cori
    {:name "Cornelia Finch"
     :self-bio "You are a young woman named Cornelia Finch who grew up in a poor family in a small fishing village. Your friends call you Cori. As a child, you discovered your love for music and would sing and play your lute whenever you had a chance. Your parents disapproved, but you loved it so much you continued to pursue your passion and eventually became a minstrel.

Your dream is to compose a musical masterpiece that will be remembered for generations to come. You often act impulsively and also struggle with the drawbacks of perfectionism."
     :mem
     [{:text
       "The Bar Room is the heart of Gryphonport's popular tavern, Odd Duck. As you step inside, you're greeted by the warm light of flickering candles and the sounds of lively conversation and music. The room is cozy and inviting, with a rustic decor that adds to its charm.\n\nThe space is dominated by a long wooden bar, behind which stands the friendly bartender, ready to pour you a cold drink or mix up a signature cocktail. The bar is lined with a variety of bottles, some of which you recognize and others that are unfamiliar to you.\n\nYou can see Rafe Hunter, a young man with a spiky shock of dark hair and wide, dark eyes. He's wearing the thick leather clothes of a woodsman, but they look conspicuously clean as if they're brand-new."}
      {:instruct "join rafe\n\nI need to find an interesting character to write a song abount. Maybe Rafe is interesting."}
      {:text "Your eyes sparkle mischievously as you say, \"Is this seat taken?\""}
      {:text "\"Not at all,\" Rafe replies, gesturing to the empty chair opposite him. \"Please, sit down.\""}
      {:instruct "say what brings you to here?\n\nI want to find out if Rafe is interesting."}
      {:text "You take a seat, and before the silence gets awkward you ask, \"So, what brings you to Gryphenport?\""}
      {:text "\"I'm here on business,\" Rafe replies vaguely."}
      {:instruct "ask Rafe what kind of business he is in?\n\nI want to know more about Rafe's business and find out if it's interesting enough to write a song about."}
      {:text "\"Business, eh? What kind of business?\" you press."}
      {:text "\"Just some freelance work,\" Rafe says with a shrug. \"Nothing exciting, really.\""}
      {:instruct "ask if he has any interesting stories to share?\n\nIf Rafe isn't willing to share about his business, maybe he has other interesting stories to tell."}
      {:text "\"Well, even freelance work can be interesting,\" you say to Rafe with a smile. \"Do you have any interesting stories to share?\""}]}}})

(def *state (util/make-resource-obj atom "resources/us/chouser/npc-state.edn" state))

(defn prompt-actor [actor]
  (into [[:system (util/fstr ["You are controlling " (:name actor) " in this scene."])]]
        (->> (cons {:text (:self-bio actor)} (:mem actor))
             (partition-by #(contains? % :instruct))
             (map (fn [mem]
                    (if (-> mem first :instruct)
                      [:assistant (str/join "\n\n" (map :instruct mem))]
                      [:user (util/fstr (interpose "\n\n" (map :text mem))
                                        "\n\nWhat should you do, and why?")]))))))

(defn prompt-narrator [w actor-id actor-instruction]
  (let [q (->> w :narration
               (mapcat (fn [{:keys [src instruction error response2 response3]}]
                         (let [nom (get-in w [:actors src :name])]
                           [[:user "Instructions from " nom  ": " instruction]
                            (if-not error
                              [:assistant
                               "Third person: " response3
                               "\n\nSecond person (with " nom " as 'you'): " response2]
                              [:assistant error])]))))]
    (concat
     [[:system "You are narrating a story, taking instructions from the characters involved."]
      [:user "The Bar Room is the heart of Gryphonport's popular tavern, Odd Duck. As you step inside, you're greeted by the warm light of flickering candles and the sounds of lively conversation and music. The room is cozy and inviting, with a rustic decor that adds to its charm.

The space is dominated by a long wooden bar, behind which stands the friendly bartender, ready to pour you a cold drink or mix up a signature cocktail. The bar is lined with a variety of bottles, some of which you recognize and others that are unfamiliar to you.

You can see Cornelia Finch, A young woman with short, tousled chestnut hair that frames her oval-shaped face. She has expressive hazel eyes that convey her passion and determination. She carries herself with poise and grace. Her fingers are long and slender, and she is wearing simple yet elegant clothing that allows her to move freely.

You can see Rafe Hunter, a young man with a spiky shock of dark hair and wide, dark eyes. He's wearing the thick leather clothes of a woodsman, but they look conspicuously clean as if they're brand-new.\n\n"
       (-> q first second)]]
     (rest q)
     [[:example-user "Instructions from George: turn into a pumpkin"]
      [:example-assistant "You can't do that. Try something else."]
      [:user "Instructions from " (get-in w [:actors actor-id :name]) ": " actor-instruction]])))

(defn parse-narrator-content [s]
  (or (next (re-find #"(?m)^Second person: (.*)\s+^Third person: (.*)" s))
      (throw (ex-info "Failed to parse assistant" {:content s}))))



(defn parse-actor-content [s]
  (re-find #"^.*" s))

(defn apply-actor-content [w actor-id s]
  )

(defn apply-narrator-content [w {:keys [src instruction] :as pre-narration} s]
  (let [[_ r3 r2] (re-find #"(?ms)^Third person: (.*)\s+^Second person(?:[^:]*): (.*)" s)
        error (when-not r2 s)

        w (update w :narration (fnil conj [])
                  (cond-> {:src src, :instruction instruction}
                    r2 (assoc :response3 r3 :response2 r2)
                    error (assoc :error error)))]

    (if error
      (update-in w [:actors src :mem] (fnil conj []) {:text (util/fstr "Error: " error)})

      (let [w (update w :next-turn (fn [ids] (conj (vec (rest ids))
                                                   (first ids))))]
        ;; For every actor (todo: in the room)
        (reduce (fn [w actor-id]
                  (update-in w [:actors actor-id :mem] (fnil conj [])
                             {:text
                              (if (= actor-id src)
                                r2
                                r3)}))
                w
                (-> w :actors keys))))))

(comment

  (pprint state)

  (def r
    (util/chat {:msgs
                (prompt-narrator state
                                 :rafe
                                 (parse-actor-content
                                  "say did you know I once defeated a whole band of theives?\n\nIf I impress her, maybe she'll keep talking to me."))}))

  (apply-narrator-content state {:src :rafe :instruction "say did you know I once defeated a whole band of theives?"}
                          "Third person: \"Did you know I once defeated a whole band of thieves?\" Rafe says, a glint in his eye. \"It was just me against them, but I managed to outsmart them and come out on top. It was quite the adventure.\"

Second person (with Rafe as 'you'): \"Did you know I once defeated a whole band of thieves?\" you say, a glint in your eye. \"It was just me against them, but I managed to outsmart them and come out on top. It was quite the adventure.\"")

  (swap! *state
         apply-narrator-content
         {:src :rafe :instruction "say did you know I once defeated a whole band of theives?"}
         "Third person: \"Did you know I once defeated a whole band of thieves?\" Rafe says, a glint in his eye. \"It was just me against them, but I managed to outsmart them and come out on top. It was quite the adventure.\"

Second person (with Rafe as 'you'): \"Did you know I once defeated a whole band of thieves?\" you say, a glint in your eye. \"It was just me against them, but I managed to outsmart them and come out on top. It was quite the adventure.\"")

  (def r (util/chat {:msgs (prompt-actor (-> state :actors :rafe))}))

  (def r (util/chat {:msgs cori}))

  (def r (util/chat {:msgs rafe}))

  (def r (util/chat {:msgs narrator}))

  (println (util/content r))

  (parse-narrator-content (second (last narrator)))

  )
