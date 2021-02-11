(ns fbeyer.prioritize.events
  (:require
   [clojure.core.async :as async]
   [re-frame.core :as re-frame]
   [fbeyer.prioritize.db :as db]))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(re-frame/reg-event-fx
 ::prioritization-requested
 (fn [{:keys [db]} _]
   {:db (assoc db :panel :prompt)
    ::start (:choices db)}))

(re-frame/reg-event-fx
 ::alternative-chosen
 (fn [_ [_ alt]]
   {::pick alt}))

;; TODO: Better name
(re-frame/reg-event-db
 ::prompt
 (fn [db [_ left right]]
   (assoc db :prompt [left right])))

(defn prio [choices ch]
  (async/go-loop
   [remaining (set choices)
    ordered []
    cache {}]
    (let [[winner cache]
          (loop [choices remaining
                 cache cache]
            (println "Finding winners among" choices)
            (if (>= (count choices) 2)
              (let [opts (shuffle choices)
                    [winners cache]
                    (loop [winners (if (odd? (count opts)) [(last opts)] [])
                           [[a b] & rest] (partition 2 opts)
                           cache cache]
                      #_(println "Pairwise winners:" winners)
                      (let [[winner cache]
                            (if-let [winner (get cache [a b])]
                              (do
                                (println "Cache hit" a b "->" winner)
                                [winner cache])
                              (do
                                #_(println "Not in cache, prompting:" a b)
                                (re-frame/dispatch [::prompt a b])
                                (let [winner (async/<! ch)]
                                  #_(println "Chosen:" a b "->" winner)
                                  [winner (-> cache (assoc [a b] winner) (assoc [b a] winner))])))
                            winners (conj winners winner)]
                        (if (seq rest)
                          (recur winners rest cache)
                          [winners cache])))]
                (println "Winners of this round:" winners)
                (if (= 1 (count winners))
                  [(first winners) cache]
                  (recur winners cache)))
              [(first choices) cache]))]
      (println "Winner:" winner ordered)
      (if winner
        (recur (disj remaining winner) (conj ordered winner) cache)
        (do
          (println "Done!")
          ordered)))))

;; TODO: Is that a co-effect?
(def prio-channel (atom nil))

;; Effect handler to start a prioritization
(re-frame/reg-fx
 ::start
 (fn [choices]
   (let [ch (async/chan)]
     (swap! prio-channel
            (fn [old-ch]
              (when old-ch
                (async/close! old-ch))
              ch))
     (prio choices ch))))

;; Effect handler to update the prioritization
(re-frame/reg-fx
 ::pick
 (fn [alternative]
   (when-let [ch @prio-channel]
     (async/offer! ch alternative))))
