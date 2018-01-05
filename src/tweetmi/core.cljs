(ns tweetmi.core
  (:require [reagent.core :as reagent]
            [axiom-cljs.core :as ax]
            [clojure.string :as str])
  (:require-macros [axiom-cljs.macros :refer [defview defquery user]]))

;; This is so that prints will show in the brower's console.
;; Should be removed for production.
(enable-console-print!)

;; A utility function that returns a callback suitable for :on-change.
;; The callback will call the given swap! function to modify the given field value.
(defn update-field [swap! field]
  )

;; This is a view.  It tracks facts (in this case, tweets made by the logged-in user),
;; and exposes them as a function of the same name.
(defview tweet-view [me]
  [:tweetmi/tweeted me text ts attrs]
  ;; Allow UI updates on data updates (reagent)
  :store-in (reagent/atom nil)
  ;; Order by timestamp, descending.
  :order-by (- ts)
  ;; The logged-in user alone can modify or delete tweets created by this view
  ;; (this is the default)
  :writers #{$user}
  ;; Restricted tweets can only be read by users I follow.
  ;; Unrestricted tweets can be read by anyone
  :readers (if (:restricted attrs)
             #{[:tweetmi.core/follower me]}
             #{}))

;; This function creates the tweets pane
(defn tweets-pane [host]
  ;; The tweet-view function returns all tweets made by the user
  (let [tweets (tweet-view host (user host))
        {:keys [add]} (meta tweets)]
    ;; This is reagent's hickup-like syntax for generating HTML
    [:div
     [:h2 "Tweets"]
     ;; When a button is clicked we call the view's add method
     ;; and provide it a map containing all the fields we wish to put in a new (empty) tweet.
     [:button {:on-click #(add {:text ""
                                ;; The host has a :time method which tells the time...
                                :ts ((:time host))
                                :attrs {}})} "tweet!"]
     [:button {:on-click #(add {:text ""
                                ;; The host has a :time method which tells the time...
                                :ts ((:time host))
                                :attrs {:restricted true}})} "tweet restricted!"]
     [:ul
      ;; We now iterate over the results.
      ;; swap! and del! are functions that alow us to modify or delete this specific tweet.
      (for [{:keys [me text ts attrs swap! del!]} tweets]
        ;; React (and hence, reagent) require that items in a list shall have unique keys.
        ;; We use the tweets' timestamps to identify them.
        [:li {:key ts}
         ;; This input box is bound bidirectionally with the stored tweet.
         ;; Its :value property comes from the stored tweet,
         ;; and its :on-change callback uses the swap! function associated with this tweet
         ;; to modify the tweet's text every time the input field's text changes.
         [:input {:value text
                  :on-change #(swap! assoc :text (.-target.value %))
                  :style {:color (if (:restricted attrs) "red" "black")}}]
         ;; The del! function deletes this tweet, so it can be used as the :on-click handler
         ;; of the delete button.
         [:button {:on-click del!} "X"]])]]))

;; This atom stores the name of a new followee (a user we would like to follow) while it is being edited.
(defonce new-followee (reagent/atom ""))

;; This view tracks all this user's followees (users he or she follows).
(defview following-view [me]
  [:tweetmi/follows me followee]
  :store-in (reagent/atom nil)
  ;; Alphabetical order
  :order-by followee)

;; This query tracs followers of the current user
(defquery followers-query [me]
  [:tweetmi/follower me -> follower]
  :store-in (reagent/atom nil)
  ;; Alphabetical order
  :order-by follower)

;; This view will return a list of size 1 if u1 follows u2, and 0 otherwise.
;; Adding an empty element to this view will make u1 follow u2.
(defview follow-view [u1 u2]
  [:tweetmi/follows u1 u2]
  :store-in (reagent/atom nil))

;; This function creates a follow/unfollow button, that controls whether u1 follows u2.
(defn follow-button [host u1 u2]
  ;; First, we evaluate the view to see if u1 already follows u2 or not.
  (let [f (follow-view host u1 u2)
        {:keys [add]} (meta f)]
    (cond (> (count f) 0)
          ;; If we do, we create an unfollow button associated with the del! function of the one record in this view.
          (for [{:keys [del!]} f]
            [:button {:key 0
                      :on-click del!} "unfollow"])
          :else
          ;; If u1 does not follow u2, we create a follow button that adds an empty map.
          ;; The view will add u1 and u2 as the follower and followee to create this relationship.
          [:button {:on-click #(add {})} "follow"])))

;; This function shows the "following" pane.
(defn following-pane [host]
  [:div
   [:h2 "Followers"]
   [:ul
    (doall (for [{:keys [follower]} (followers-query host (user host))]
             [:li {:key follower}
              follower (follow-button host (user host) follower)]))]
   [:h2 "Following"]
   ;; Query all the users the current user follows
   (let [followees (following-view host (user host))
         {:keys [add]} (meta followees)]
     [:form
      ;; The name of a new user to follow.
      ;; This is simple reagent-style binding.
      [:input {:value @new-followee
               :on-change #(reset! new-followee (-> % .-target .-value))}]
      ;; When "follow" is clicked...
      [:input {:type "submit"
               :value "follow"
               :on-click #(do
                            ;; Add a "follows" relationship, between the current user and
                            ;; the user who's name was written in the input box, and
                            (add {:followee @new-followee})
                            ;; Clear the input box.
                            (reset! new-followee ""))}]
      [:ul
       ;; Iterate over the followees
       (for [{:keys [followee del!]} followees]
         ;; List them...  The followee name is its own unique key
         [:li {:key followee}
          followee
          ;; The del! function is used to unfollow a followee.
          [:button {:on-click del!} "unfollow"]])]])])


;; Timelines can be huge and full of very old, uninteresting tweets.
;; As we would like to only see the most recent ones (and not spend resources on the old ones)
;; we can provide the timeline query a range of day indexes (counting since 1-1-1970),
;; and the query will only provide us with these.
;; To know which days are relevant we have to calculate the current day index.
(defn this-day [host]
  (let [ms-in-day (* 1000 60 60 24)]
    (quot ((:time host)) ms-in-day)))

;; Which day ranges are we looking at?
(defonce day-ranges (reagent/atom [[-2 5]])) ;; One week by default, going 1 day into the future

;; This query will give us the tweets in the currrent user's timeline for the given day-range. 
(defquery timline-query [me day-from day-to]
  [:tweetmi/timeline me day-from day-to -> author tweet ts]
  :store-in (reagent/atom nil)
  :order-by (- ts))

;; The timeline pane
(defn timeline-pane [host]
  [:div
   [:h2 "Timeline"]
   [:ul
    ;; Flatten the two levels of the list
    (doall (apply concat
                  ;; For each range...
                  (for [[to-days-ago from-days-ago] @day-ranges]
                    ;; Fetch all timeline entries (tweets by followed users) in the desired day range
                    (for [{:keys [author tweet ts]} (timline-query host
                                                                   (user host)
                                                                   (- (this-day host) from-days-ago)
                                                                   (- (this-day host) to-days-ago))]
                      [:li {:key ts}
                       (str author ": '" tweet "'")]))))]
   ;; When clicked, adds a range of three more days.
   [:button {:on-click #(swap! day-ranges
                               (fn [ranges]
                                 (let [[from to] (last ranges)]
                                   (conj ranges [to (+ to 3)]))))}
    "Show older..."]])

(defview dbg [user day]
  [:tweetmi.core/followee-tweets [user day] author tweet ts]
  :store-in (reagent/atom nil))

(defn dbg-pane [host]
  [:ul
   (for [{:keys [author tweet ts]} (dbg host (user host) (this-day host))]
     [:li {:key tweet}
      author ": " tweet " (" ts ")"])])

;; The main page function
(defn twitting [host]
  [:div
   [:h1 "Hi, " (user host)]
   [:table
    [:tbody
     [:tr.panes
      [:td.pane (tweets-pane host)]
      [:td.pane (timeline-pane host)]
      [:td.pane (following-pane host)]]]]])

;; The host object holds communication to the host.
;; We provide it with a reagent atom so that we can track its state from the UI.
(defonce host (ax/default-connection reagent/atom))

;; Renderring the page...
(when-let [app-elem (js/document.getElementById "app")]
  (reagent/render [twitting host] app-elem))
