(ns fbeyer.prioritize.events
  (:require
   [re-frame.core :as re-frame]
   [fbeyer.prioritize.db :as db]
   ))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))
