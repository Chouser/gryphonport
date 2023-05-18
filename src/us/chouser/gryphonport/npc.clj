(ns us.chouser.gryphonport.npc
  (:require [us.chouser.gryphonport.util :as util]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [us.chouser.gryphonport.location-graph :as loc]
            [malli.core :as m]
            [us.chouser.gryphonport.location-descriptions :as desc]))

(defn prompt-actor [state actor-id]
  (let [actor (-> state :actors actor-id)]
    (into [[:system (util/fstr ["You are controlling " (:name actor) " in this scene."])]]
          (->> (concat [{:text (:self-bio actor)}]
                       (:mem actor)
                       [{:text (util/fstr
                                "You are currently in " (:name (loc/node state (:loc actor))) ". "
                                "From here you could travel-to " (->> (:loc actor)
                                                                   (loc/nav-adj-node state)
                                                                   (map :name)
                                                                   (util/flist ", " "or ")) "."
                                "\nChoose between the commands `say` and `travel-to`.")}])
               (partition-by #(contains? % :text))
               (map (fn [mems]
                      (if (-> mems first :text)
                        [:user (interpose "\n\n" (map :text mems))
                         "\nWhat should you do, and why?"]
                        [:assistant (->> mems
                                         (map (fn [mem]
                                                [(cond
                                                   (:say mem) ["say: " (:say mem)]
                                                   (:travel-to mem) ["travel-to: " (:name (loc/node state (:travel-to mem)))])
                                                 "\n\nreason: " (:reason mem)]))
                                         (interpose "\n\n")
                                         util/fstr)])))))))

(defn gen-narrator-user [w {:keys [src loc] :as mem}]
  (let [loc-name (:name (loc/node w loc))
        [verb arg] (some (partial find mem) [:travel-to :say])]
    [(-> w :actors src :name) " is in " loc-name " and wants to " (name verb) ": "
     (case verb
       :say arg
       :travel-to (:name (loc/node w arg)))]))

(defn prompt-narrator [w actor-instruction]
  (let [q (->> w :narration
               (mapcat (fn [{:keys [src error travel-to response2] :as mem}]
                         [[:user (gen-narrator-user w mem)]
                          [:assistant
                           (or error
                               (if travel-to
                                 ["Third person coming:" (:response3-coming mem)
                                  "\nThird person going:" (:response3-going mem)
                                  "\nSecond person: " response2]
                                 ["Third person: " (:response3 mem)
                                  "\nSecond person: " response2]))]])))]
    (concat
     [[:system "You are narrating a story, taking instructions for the characters involved."]
      [:user
       (:description (loc/node w (:loc actor-instruction)))
       "\n\n"
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

      [:user
       (gen-narrator-user w actor-instruction)
       "\n\n"
       (let [nom (-> w :actors (:src actor-instruction) :name)]
         (if (:travel-to actor-instruction)
           ["If that instruction makes sense, describe it in three formats: "
            "Third person coming (for someone observing " nom " entering " "the travel from the "
            "destination location), Third person going (for someone watching them "
            "leave), and Second person (for " nom ", like 'going' with 'you'"
            "instead of " nom ")."]
           ["If that instruction makes sense, describe it in two formats: Third "
            "person (for someone observing), and Second person (like Third person "
            "with 'you' instead of " nom ")."]))]])))

(m/=> parse-actor-content
      [:=> [:cat :any keyword? string?]
       [:map {:closed true}
        [:loc keyword?]
        [:say {:optional true} string?]
        [:travel-to {:optional true} keyword?]
        [:reason string?]]])
(defn parse-actor-content [w loc s]
  (let [m (->> (re-seq #"(?m)^(say|travel-to|reason): (.*)" s)
               (map (fn [[_ k v]]
                      [(keyword k) v]))
               (into {:loc loc}))
        tt (:travel-to m)]
    (if-not tt
      m
      (let [adj (loc/nav-adj-node w loc)
            to-ids (keep #(when (<= 0 (.indexOf tt (:name %)))
                            (:id %))
                         adj)]
        (condp >= (count to-ids)
          0 (throw (ex-info (str "Bad travel-to target: " tt)
                            {:actor-mem {:text (util/fstr "You cannot travel-to " tt " from here.")}
                             :loc loc, :tt tt, :adj-names (map :name adj)}))
          1 (assoc m :travel-to (first to-ids))
          (throw (ex-info (str "Too many matching travel-to targets: " tt)
                          {:loc loc, :tt tt, :adj-names (map :name adj)})))))))

(defn apply-actor-content [w actor-id parsed-instruction]
  (update-in w [:actors actor-id :mem] (fnil conj []) parsed-instruction))

(m/=> parse-narrator-content
      [:=> [:cat string?]
       [:or
        [:map {:closed true}
         [:error string?]]
        [:map {:closed true}
         [:response2 string?]
         [:response3 string?]]
        [:map {:closed true}
         [:response2 string?]
         [:response3-coming string?]
         [:response3-going string?]]]])
(defn parse-narrator-content [narrator-response]
  (if-let [parts (seq (re-seq #"((?:Third|Second) person[^:]*): (.*)"
                              narrator-response))]
    (->>
     parts
     (map (fn [[_ k v]]
            (let [[_ r2 r3 c g p] (re-find #"(Second)|(Third).*(coming)|(going)|(person:)" k)]
              [(keyword (str (if r2 "response2" "response3")
                             (cond
                               c "-coming"
                               g "-going")))
               (str/trim v)])))
     (into {}))
    {:error narrator-response}))

(m/=> apply-narrator-content
      [:=> [:cat :any [:map {:closed true}
                       [:src keyword?]
                       [:loc keyword?]
                       [:say {:optional true} string?]
                       [:travel-to {:optional true} keyword?]
                       [:error {:optional true} string?]
                       [:response2 {:optional true} string?]
                       [:response3 {:optional true} string?]
                       [:response3-coming {:optional true} string?]
                       [:response3-going {:optional true} string?]]]
       :any])
(defn apply-narrator-content
  [w {:keys [src error] :as m}]
  (when (not= src (-> w :next-turn first))
    (println "WARNING: next-turn is" (-> w :next-turn first) "but using src" src))
  (when (not= (:loc m) (-> w :actors src :loc))
    (println "WARNING:" src "loc is" (-> w :actors src :loc) "but using" (:loc m)))
  (let [w (update w :narration (fnil conj []) m)]
    (if error
      (update-in w [:actors src :mem] (fnil conj [])
                 {:text (util/fstr "Error: " (str/replace error #"Error: " ""))})
      (let [loc-set #{(:loc m) (:travel-to m)}
            w (-> w
                  (update :next-turn (fn [ids] (conj (vec (rest ids))
                                                     (first ids))))
                  (cond-> (:travel-to m) ;; TODO compute actual move destination before narration?
                    (desc/move-thing [:actors src :loc] (:travel-to m))))]
        ;; For every actor
        (reduce (fn [w actor-id]
                  (let [other-loc (-> w :actors actor-id :loc)]
                    (if-not (contains? loc-set other-loc)
                      w
                      (update-in w [:actors actor-id :mem] (fnil conj [])
                                 {:text
                                  (util/fstr
                                   (cond
                                     (= actor-id src)
                                     , (let [dest (loc/node w (:travel-to m))]
                                         [(:response2 m) "\n\n"
                                          (:description dest) "\n\n"
                                          (->> w :actors
                                               (map (fn [[_ a]]
                                                      ["Here you can see " (:name a) ", "
                                                       (:description a) "\n"])))])
                                     (= other-loc (:travel-to m)) [(:response3-coming m) "\n\n"
                                                                   (-> w :actors src :name) " is "
                                                                   (-> w :actors src :description)]
                                     :else (or (:response3 m) (:response3-going m))))}))))
                w
                (-> w :actors keys))))))

(malli.instrument/instrument!)

(comment

  (apply-actor-content @*state :rafe
                       "say did you know I once defeated a whole band of theives?\n\nIf I impress her, maybe she'll keep talking to me.")

  (def r (util/chat {:msgs (prompt-narrator @*state
                                            (merge {:src :rafe}
                                             (parse-actor-content
                                              @*state
                                              (-> @*state :actors :rafe :loc)
                                              "say: Well, it all started when a wealthy merchant hired me to find a lost artifact...\n\nreason: I'm happy to share the details of my adventure, and it might be interesting to her. However, I won't go into too much detail, just in case she's not trustworthy.")))}))

  (println (util/content r))

  (def r (util/chat {:msgs (prompt-actor
                            (update-in (-> @*state :actors :cori)
                                       [:mem]
                                       conj
                                       {:text "Please write the full text of a song about Rafe"}))}))

  (->> @*state :actors :cori :mem
       (map :text)
       (interpose "\n")
       util/fstr
       println)

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
