(ns tweetmi.core-test
  (:use midje.sweet)
  (:require [tweetmi.core :refer :all]
            [cloudlog-events.testing :refer [scenario as query emit apply-rules]]))

(fact
 (let [ts (+ (* 3 msec-in-day) 1234)]
   (scenario
    (as "bob"
        (emit [:tweetmi/tweeted "bob" "hello" ts {}]))
    (as "alice"
        (emit [:tweetmi/follows "alice" "bob"]))
    (apply-rules [:tweetmi.core/followee-tweets ["alice" 3]]))
   => #{["bob" "hello" ts]}))

(fact
 (scenario
  (as "bob"
      (emit [:tweetmi/tweeted "bob" "Sunday" (* 1 msec-in-day) {}])
      (emit [:tweetmi/tweeted "bob" "Monday" (* 2 msec-in-day) {}])
      (emit [:tweetmi/tweeted "bob" "Tuesday" (* 3 msec-in-day) {}])
      (emit [:tweetmi/tweeted "bob" "Wednesday" (* 4 msec-in-day) {}]))
  (as "charlie"
      (emit [:tweetmi/tweeted "charlie" "This is for @alice" (* 2 msec-in-day) {}])
      (emit [:tweetmi/tweeted "charlie" "This is not for alice" (* 2 msec-in-day) {}]))
  (as "alice"
      (emit [:tweetmi/tweeted "alice" "sunday" (* 1 msec-in-day) {}])
      (emit [:tweetmi/tweeted "alice" "monday" (* 2 msec-in-day) {}])
      (emit [:tweetmi/tweeted "alice" "tuesday" (* 3 msec-in-day) {}])
      (emit [:tweetmi/tweeted "alice" "wednesday" (* 4 msec-in-day) {}])
      (emit [:tweetmi/follows "alice" "bob"])
      (query [:tweetmi/timeline "alice" 2 4])
      => #{["bob" "Monday" (* 2 msec-in-day)]
           ["bob" "Tuesday" (* 3 msec-in-day)]
           ["alice" "monday" (* 2 msec-in-day)]
           ["alice" "tuesday" (* 3 msec-in-day)]
           ["charlie" "This is for @alice" (* 2 msec-in-day)]}
      ;; The query should protect itself, and not answer queries for more than 20 days at a time
      (query [:tweetmi/timeline "alice" 2 23]) => map?)))

(fact
 (scenario
  (as "alice"
      (emit [:tweetmi/follows "alice" "bob"])
      (emit [:tweetmi/follows "alice" "charlie"]))
  (as "eve"
      (emit [:tweetmi/follows "eve" "bob"])
      (emit [:tweetmi/follows "foo" "bob"]))
  (apply-rules [:tweetmi.core/follower "bob"])
  => #{["alice"] ["eve"]}
  (as "bob"
      (query [:tweetmi/follower "bob"])) => #{["alice"] ["eve"]}))

