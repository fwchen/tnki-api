(ns tnki.core
  (:require [tnki.dao :as dao]
            [tnki.middle :as middle]
            [tnki.auth :as auth]
            [tnki.util :as util]
            [tnki.router.auth :as router-auth]
            [tnki.router.card :as router-card]
            [tnki.router.file :as router-file]
            [cljs.core.async :as async]
            [cljs.nodejs :as nodejs]
            [clojure.string :as string]
            [cljs-time.core :as cljstime]
            [cljs-time.coerce :as cljstime-coerce])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(nodejs/enable-util-print!)

(defonce express (nodejs/require "express"))
(defonce body-parser (nodejs/require "body-parser"))
(defonce http (nodejs/require "http"))
(defonce https (nodejs/require "https"))
(defonce fs (nodejs/require "fs"))
(defonce path (nodejs/require "path"))
(defonce bcrypt (nodejs/require "bcryptjs"))
(defonce moment (nodejs/require "moment"))
(def knex ((nodejs/require "knex")
           (clj->js {:client "sqlite3"
                     :connection {:filename "./db.sqlite3"}
                     :useNullAsDefault true})))

(def app (express))
(.use app (.json body-parser))

(. app (get "/api/hello"
            (fn [req res]
              (.send res "hello world!"))))


(. app (get "/api/daily/statistics"
            middle/auth-jwt
            middle/insure-today-statistics
            (fn [req res]
              (go
                (let [user (.-user req)
                      email (:email user)
                      all-finish (async/<! (dao/get-all-finish-card-count-chan user))
                      total-days (async/<! (dao/get-user-total-learn-days-chan user))
                      need-learn-card {:need_learn_count (async/<! (dao/get-user-need-learning-card-count user))}
                      statistics-table-data (async/<! (dao/get-user-daily-statistics user))
                      ;; TODO not merge
                      statistics-data (merge need-learn-card
                                             total-days
                                             all-finish
                                             statistics-table-data)]
                  (.send res (clj->js statistics-data))
                  )))))

(.use app router-auth/router)
(.use app router-card/router)
(.use app router-file/router)

(defn -main [& args]
  (doto (.createServer http #(app %1 %2))
    (.listen 3000 "127.0.0.1"))
  (doto (.createServer https (clj->js
                              {:key (.readFileSync fs "/etc/letsencrypt/live/tnki.octopuese.xyz/privkey.pem")
                               :cert (.readFileSync fs "/etc/letsencrypt/live/tnki.octopuese.xyz/cert.pem")})
                       app)
    (.listen 3001 "127.0.0.1"))
  (println "server listen on http://localhost:3000"))

(set! *main-cli-fn* -main)
