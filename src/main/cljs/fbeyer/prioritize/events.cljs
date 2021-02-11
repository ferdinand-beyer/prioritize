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

(re-frame/reg-event-db
 ::prioritization-completed
 (fn [db [_ choices]]
   (-> db
       (assoc :choices choices)
       (assoc :panel :choices))))

(re-frame/reg-event-fx
 ::alternative-chosen
 (fn [_ [_ alt]]
   {::pick alt}))

;; TODO: Better name
(re-frame/reg-event-db
 ::prompt
 (fn [db [_ left right]]
   (assoc db :prompt [left right])))

(defn- interactive-pick
  "Asynchronously asks the user to pick between a number of choices."
  [response-ch in out]
  (async/go-loop
   [cache {}]
    (when-let [choices (async/<! in)]
      (let [cache-key (sort choices)
            [pick cache]
            (if-let [pick (get cache cache-key)]
              [pick cache]
              (do
                ; TODO: Move this behind a channel, then the prioritization logic
                ; can stay independent of re-frame!
                (re-frame/dispatch (apply vector ::prompt choices))
                (let [pick (async/<! response-ch)]
                  [pick (assoc cache cache-key pick)])))]
        (when (async/>! out pick)
          #_(println "Picked:" pick "from" choices "- Cache:" cache)
          (recur cache))))))

(defn- prioritize
  "Prioritize choices using pick-ch to interactively pick the item with
   higher priority."
  [choices pick-ch]
  (async/go-loop
   [remaining (set choices)
    ordered []]
    (if-let [winner
             (loop [choices remaining]
               (if (< (count choices) 2)
                 (first choices)
                 (let [choices (shuffle choices)
                       winners
                       (loop [winners (if (odd? (count choices))
                                        [(last choices)]
                                        [])
                              [pair & rest] (partition 2 choices)]
                         (async/>! pick-ch pair)
                         (when-let [winner (async/<! pick-ch)]
                           (let [winners (conj winners winner)]
                             (if rest
                               (recur winners rest)
                               winners))))]
                   (if (= 1 (count winners))
                     (first winners)
                     (recur winners)))))]
      (recur (disj remaining winner) (conj ordered winner))
      ordered)))

(defn run-prioritization [choices ch]
  (let [pick-ch (async/chan)
        _ (interactive-pick ch pick-ch pick-ch)
        prio-ch (prioritize choices pick-ch)]
    (async/go (re-frame/dispatch [::prioritization-completed (async/<! prio-ch)]))))

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
     (run-prioritization choices ch))))

;; Effect handler to update the prioritization
(re-frame/reg-fx
 ::pick
 (fn [alternative]
   (when-let [ch @prio-channel]
     (async/offer! ch alternative))))
