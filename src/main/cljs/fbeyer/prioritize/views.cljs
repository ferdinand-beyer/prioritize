(ns fbeyer.prioritize.views
  (:require
   [re-frame.core :as re-frame]
   [fbeyer.prioritize.events :as e]
   [fbeyer.prioritize.subs :as subs]
   ))

(defn choices-panel []
  (let [choices (re-frame/subscribe [::subs/choices])]
    [:div
     [:h2 "Choices!"]
     [:ol
      (for [choice @choices]
        ^{:key choice} [:li [:a choice]])]
     [:button
      {:on-click #(re-frame/dispatch [::e/prioritization-requested])}
      "Prioritize!"]]))

(defn prompt-panel []
  (let [prompt (re-frame/subscribe [::subs/prompt])]
    [:div
     [:h2 "Which one is more important?"]
     (when-let [[left right] @prompt]
       [:ul
        [:li [:button {:on-click #(re-frame/dispatch [::e/alternative-chosen left])} left]]
        [:li [:button {:on-click #(re-frame/dispatch [::e/alternative-chosen right])} right]]])]))

(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])
        panel (re-frame/subscribe [::subs/active-panel])]
    [:div
     [:h1 "Hello from " @name]
     (case @panel
       :edit-choices [choices-panel]
       :prompt [prompt-panel]
       [:em (str "Error: Invalid Panel" @panel)])
     ]))
