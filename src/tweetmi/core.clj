(ns tweetmi.core
  (:require [permacode.core :as perm]
            [perm.QmNYKXgUt64cvXau5aNFqvTrjyU8hEKdnhkvtcUphacJaf :as clg]))

(perm/pure
 (def msec-in-day (* 1000 60 60 24))

 ;; Rules should reside in their own source file
 (defn ts-to-day [ts]
   (quot ts msec-in-day))

 (clg/defrule followee-tweets [[user day] author text ts]
   [:tweetmi/follows user author] (clg/by user)
   [:tweetmi/tweeted author text ts attrs] (clg/by author)
   (let [day (ts-to-day ts)]))

 (defn days-in-range [from-day to-day]
   (let [day-range (range from-day to-day)]
     (if (> (count day-range) 20)
       []
       day-range)))

 (clg/defclause tl-1
   [:tweetmi/timeline user from-day to-day -> author text ts]
   (for [day (days-in-range from-day to-day)])
   [followee-tweets [user day] author text ts])

 (clg/defclause tl-2
   [:tweetmi/timeline user from-day to-day -> user text ts]
   [:tweetmi/tweeted user text ts attrs] (clg/by user)
   (for [day (days-in-range from-day to-day)])
   (when (= (ts-to-day ts) day)))

 (clg/defrule tweet-by-mention [[user day] author text ts]
   [:tweetmi/tweeted author text ts attrs] (clg/by author)
   (let [day (ts-to-day ts)])
   (for [user-handle (re-seq #"@[a-zA-Z0-9]+" text)])
   (let [user (subs user-handle 1)]))

 (clg/defclause tl-3
   [:tweetmi/timeline user from-day to-day -> author text ts]
   (for [day (days-in-range from-day to-day)])
   [tweet-by-mention [user day] author text ts])

 (clg/defrule follower [user f]
   [:tweetmi/follows f user] (clg/by f))
 
 (clg/defclause f1
   [:tweetmi/follower user -> f]
   [follower user f]))
