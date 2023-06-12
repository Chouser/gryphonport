(ns us.chouser.gryphonport.npc
  (:require [us.chouser.gryphonport.util :as util]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [us.chouser.gryphonport.location-graph :as loc]
            [malli.core :as m]
            [us.chouser.gryphonport.location-descriptions :as desc]))

(defn prompt-actor [state actor-id]
  (prn :prompt-actor actor-id)
  (let [actor (-> state :actors actor-id)]
    (into [[:system (util/fstr ["You are controlling " (:name actor) " in this scene."])]
           [:example-user "From here you could travel to Dance Hall. What should you do, and why?"]
           [:example-assistant "travel-to: Dance Hall\n\nreason: Joe might be at the Dance Hall and I want to see him."]
           [:example-user "From here you could travel to Dance Hall. What should you do, and why?"]
           [:example-assistant "say: This will be fun!\n\nreason: I want them to know I like the idea."]]
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
                                                (or (:err-cmd mem)
                                                    [(cond
                                                       (:say mem) ["say: " (:say mem)]
                                                       (:travel-to mem) ["travel-to: " (:name (loc/node state (:travel-to mem)))])
                                                     "\n\nreason: " (:reason mem)])))
                                         (interpose "\n\n")
                                         util/fstr)])))))))

;; Travel to Main Street from Common Room
;; Bob steps out toward Main Street
;; You step out toward Main Street
;; Bob steps arrives from the Odd Duck  ** ascend tree
;;
;; Jill ducks into the Odd Duck
;; You duck into the Odd Duck
;; Jill ducks in from Main Street

(defn gen-narrator-user [w {:keys [src loc] :as mem}]
  (let [nom (-> w :actors src :name)
        [verb arg] (some (partial find mem) [:travel-to :say])]
    (case verb
      :say [nom " will say: " arg]
      :travel-to (let [from-node (loc/node w loc)
                       from-parent-node (->> from-node :parent (loc/node w))
                       to-node (loc/node w arg)]
                   [nom " will travel to " (:name (loc/node w arg))
                    " from " (:name (if (= (:parent from-parent-node)
                                           (:parent to-node))
                                      from-parent-node
                                      from-node))]))))

(defn mem-type [mem]
  (cond
    (:say mem) :say
    (:travel-to mem) :travel-to
    :else :unknown))

(m/=> prompt-narrator
      [:=> [:cat :any [:map {:closed true}
                       [:src keyword?]
                       [:loc keyword?]
                       [:say {:optional true} string?]
                       [:travel-to {:optional true} keyword?]]]
       :any])
(defn prompt-narrator [w actor-instruction]
  (prn :prompt-narrator actor-instruction)
  (let [recent-mems (->> w :narration (take-last 15))
        instruction-type (mem-type actor-instruction)
        more-examples-n (- 5 (->> recent-mems
                                  (filter #(= instruction-type (mem-type %)))
                                  count))
        more-examples (->> w :narration reverse
                           (drop (count recent-mems))
                           (filter #(= instruction-type (mem-type %)))
                           (take (max 0 more-examples-n))
                           reverse)
        q (->> (concat more-examples recent-mems)
               (mapcat (fn [mem]
                         [[:user (gen-narrator-user w mem)]
                          [:assistant
                           (or (:error mem)
                               ["Third person: " (:response3 mem)
                                "\nSecond person: " (:response2 mem)
                                (when-let [a (:arrive mem)]
                                  ["\nArriving: " a])])]])))]
    (concat
     [[:system "You are narrating a story, taking instructions for the characters involved."]
      [:user
       (->> [:loc :travel-to]
            (keep actor-instruction)
            (map (fn [loc]
                   [(:description (loc/node w loc))
                    "\n\n"])))
       ;; Append the first :user entry to this header text:
       (-> q first rest)]]
     (rest q)
     [[:user
       (gen-narrator-user w actor-instruction)
       "\n\n"
       (let [nom (get-in w [:actors (:src actor-instruction) :name])]
         (assert (seq nom))
         (if (:travel-to actor-instruction)
           ["Describe the action in three formats: Third person "
            "(for someone watching " nom " leave), Second person (like Third "
            "person but with 'you' instead of '" nom "', "
            "and Arriving (third person for someone watching " nom " arrive)."]
           ["Narrate the action of " nom ", incuding the dictation verbatim, in two formats: Third "
            "person (for someone observing), and Second person (like Third person "
            "with 'you' instead of '" nom "')."]))]])))

(m/=> parse-actor-content
      [:=> [:cat :any keyword? string?]
       [:map {:closed true}
        [:loc keyword?]
        [:say {:optional true} string?]
        [:travel-to {:optional true} keyword?]
        [:reason {:optional true} string?]]])
(defn parse-actor-content [w loc s]
  (let [m (->> (re-seq #"(?m)^(say|travel-to|reason): (.*)" s)
               (map (fn [[_ k v]]
                      [(keyword k) (str/trim v)]))
               (into {:loc loc}))
        tt (:travel-to m)]
    (cond
      (:say m) m
      tt (let [adj (loc/nav-adj-node w loc)
               to-ids (keep #(when (<= 0 (.indexOf tt (:name %)))
                               (:id %))
                            adj)]
           (condp >= (count to-ids)
             0 (throw (ex-info (str "Bad travel-to target: " tt)
                               {:actor-mem [{:err-cmd s}
                                            {:text (util/fstr "You cannot travel-to " tt " from here.")}]
                                :loc loc, :tt tt, :adj-names (map :name adj)}))
             1 (assoc m :travel-to (first to-ids))
             (throw (ex-info (str "Too many matching travel-to targets: " tt)
                             {:loc loc, :tt tt, :adj-names (map :name adj)}))))
      :else (throw (ex-info (str "Bad command: " s)
                            {:actor-mem [{:err-cmd s}
                                         {:text "That is an unsupported command."}]})))))

(defn apply-actor-content [w actor-id parsed-instruction]
  (update-in w [:actors actor-id :mem] (fnil conj []) parsed-instruction))

(m/=> parse-narrator-content
      [:=> [:cat string?]
       [:or
        [:map {:closed true}
         [:error string?]]
        [:map {:closed true}
         [:response2 string?]
         [:response3 string?]
         [:arrive {:optional true} string?]]]])
(defn parse-narrator-content [narrator-response]
  (if-let [parts (seq (re-seq #"((?:Third|Second) person|Arriving): (.*)"
                              narrator-response))]
    (->>
     parts
     (map (fn [[_ k v]]
            (let [[_ r2 r3 a] (re-find #"^(Second)|(Third)|(Arriving)" k)]
              [(cond
                 r2 :response2
                 r3 :response3
                 a :arrive)
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
                       [:arrive {:optional true} string?]]]
       :any])
(defn apply-narrator-content
  [w {:keys [src error] :as m}]
  (assert (= (boolean (:travel-to m))
             (boolean (:arrive m))))
  (when (not= src (-> w :next-turn first))
    (println "WARNING: next-turn is" (-> w :next-turn first) "but using src" src))
  (when (not= (:loc m) (-> w :actors src :loc))
    (println "WARNING:" src "loc is" (-> w :actors src :loc) "but using" (:loc m)))
  (let [w (update w :narration (fnil conj []) m)]
    (if error
      (update-in w [:actors src :mem] (fnil conj [])
                 {:text (util/fstr "Error: " (str/replace error #"Error: " ""))})
      (let [w (-> w
                  (update :next-turn (fn [ids] (conj (vec (rest ids))
                                                     (first ids))))
                  (cond-> (:travel-to m) ;; TODO compute actual move destination before narration?
                    (desc/move-thing [:actors src :loc] (:travel-to m))))
            new-loc (-> w :actors src :loc)
            loc-set (conj #{(:loc m)} new-loc)]
        (prn :new-loc new-loc :loc-set loc-set)
        ;; For every actor
        (reduce (fn [w actor-id]
                  (let [other-actor-loc (-> w :actors actor-id :loc)]
                    (if-not (contains? loc-set other-actor-loc)
                      w
                      ;; ...add to their memory
                      (update-in w [:actors actor-id :mem] (fnil conj [])
                                 {:text
                                  (if (:travel-to m)
                                    (util/fstr
                                     (cond
                                       ;; self
                                       (= actor-id src)
                                       , (let [dest (loc/node w new-loc)]
                                           [(:response2 m) "\n\n"
                                            (:description dest)
                                            (when (-> w :actors seq) "\n\n")
                                            (->> w :actors
                                                 (map (fn [[aid a]]
                                                        (when (and (not= aid actor-id)
                                                                   (= new-loc (:loc a)))
                                                          ["Here you can see " (:name a) ", "
                                                           (:description a)])))
                                                 (interpose "\n"))])
                                       ;; arriving
                                       (= other-actor-loc new-loc)
                                       , [(:arrive m) "\n\n"
                                          (-> w :actors src :name) " is "
                                          (-> w :actors src :description)]
                                       ;; still here or leaving
                                       :else (:response3 m)))
                                    (if (= actor-id src) ;; self
                                      (:response2 m)
                                      (:response3 m)))}))))
                w
                (-> w :actors keys))))))

(malli.instrument/instrument!)

(comment

  (apply-actor-content @*state :rafe
                       "say did you know I once defeated a whole band of theives?\n\nIf I impress her, maybe she'll keep talking to me.")

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
