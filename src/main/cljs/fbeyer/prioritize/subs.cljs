(ns fbeyer.prioritize.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::active-panel
 (fn [db]
   (:panel db)))

(re-frame/reg-sub
 ::choices
 (fn [db]
   (:choices db)))

(re-frame/reg-sub
 ::prompt
 (fn [db]
   (:prompt db)))