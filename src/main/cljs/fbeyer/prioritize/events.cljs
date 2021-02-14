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

(defn- run-caching-pick
  "Runs an asynchronous proceess that delegates deciding between choices using
  pick-ch, and caches results."
  [ch pick-ch]
  (async/go-loop
   [cache {}]
    (when-let [choices (async/<! ch)]
      (let [cache-key (sort choices)
            [pick cache]
            (if-let [pick (get cache cache-key)]
              [pick cache]
              (do
                (async/>! pick-ch choices)
                (when-let [pick (async/<! pick-ch)]
                  [pick (assoc cache cache-key pick)])))]
        (async/>! ch pick)
        #_(println "Picked:" pick "from" choices "- Cache:" cache)
        (recur cache)))))

(defn- prioritize-async
  "Run an asynchronous process that prioritizes choices, using ch to
  delegate decisions."
  [choices ch]
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
                         (async/>! ch pair)
                         (when-let [winner (async/<! ch)]
                           (let [winners (conj winners winner)]
                             (if rest
                               (recur winners rest)
                               winners))))]
                   (if (= 1 (count winners))
                     (first winners)
                     (recur winners)))))]
      (recur (disj remaining winner) (conj ordered winner))
      ordered)))

(defn- run-interactive-pick
  "Runs an asynchronous process that repeatedly reads choices from ch,
  dispatches them as a re-frame event to present them to the user,
  reads the result from result-ch, and forwards it back to ch."
  [ch result-ch]
  (async/go-loop
   []
   (when-let [choices (async/<! ch)]
     (re-frame/dispatch (apply vector ::prompt choices))
     (when-let [pick (async/<! result-ch)]
       (async/>! ch pick)
       (recur)))))

(defn run-prioritization [choices ch]
  (let [pick-ch (async/chan)
        cache-ch (async/chan)
        _ (run-interactive-pick pick-ch ch)
        _ (run-caching-pick cache-ch pick-ch)
        prio-ch (prioritize-async choices cache-ch)]
    (async/go
     (when-let [result (async/<! prio-ch)]
       (re-frame/dispatch [::prioritization-completed result])))))

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
