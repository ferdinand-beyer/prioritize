(ns fbeyer.prioritize.views
  (:require
   [re-frame.core :as re-frame]
   [fbeyer.prioritize.events :as e]
   [fbeyer.prioritize.subs :as subs]
   [mui-bien.core :as mui]
   ))

(defn top-bar []
  [mui/app-bar
   {:position :sticky}
   [mui/tool-bar
    [mui/typography
     {:variant :h6}
     "Prioritize!"]]])

(defn panel-container [& children]
  [mui/grid
   {:container true
    :direction :column
    :justify :flex-start
    ;:align-items :center
    :style {:min-height "100vh"}}
   [mui/grid
    {:item true
     :style {:width "100%"}}
    [top-bar]]
   [mui/grid
    {:item true
     :container true
     :justify :center
     :align-items :center
     :style {:flex-grow 1}}
    [mui/grid
     {:item true}
     children]]])

(defn prompt-panel []
  (let [prompt (re-frame/subscribe [::subs/prompt])]
    [panel-container
     [:<>
      [mui/typography {:variant :h5} "Which one is more important?"]
      (when-let [choices @prompt]
        [mui/grid
         {:container true
          :justify :center
          :align-items :center
          :spacing 3}
         (for [choice choices]
           ^{:key choice}
           [mui/grid
            {:item true}
            [mui/button
             {:variant :outlined
              :size :large
              :on-click #(re-frame/dispatch [::e/alternative-chosen choice])}
             choice]])])]]))

(defn choices-panel []
  (let [choices (re-frame/subscribe [::subs/choices])]
    [panel-container
     [mui/typography {:variant :h5} "Choices"]
     [:ol
      (for [choice @choices]
        ^{:key choice}
        [:li
         [:span choice]])]
     [mui/button
      {:variant :contained
       :color :primary
       :on-click #(re-frame/dispatch [::e/prioritization-requested])}
      "Prioritize!"]]))

(defn main-panel []
  (let [panel (re-frame/subscribe [::subs/active-panel])]
    (case @panel
      :choices [choices-panel]
      :prompt [prompt-panel]
      [:em (str "Error: Invalid Panel" @panel)])))

(defn root-panel []
  [:<>
   [mui/css-baseline]
   [main-panel]])