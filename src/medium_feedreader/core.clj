(ns medium-feedreader.core
  (:require [org.httpkit.client :as http]
            [less.awful.ssl :as ssl]
            [cheshire.core :as json]
            [clojure.string :as s]
            [ring.util.codec :as codec]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [environ.core :as env]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [schema.core :as schema]
            [plumbing.core :refer [fnk]]
            [ring-aws-lambda-adapter.core :refer [defhandler]]
            [clojure.set :as set]))

(defn medium-urls [data]
  (->> data :data
       (map :url)
       (map #(s/replace % #"medium\.com/" "medium.com/feed/"))
       (map codec/url-encode)
       (map #(str "http://ftr.fivefilters.org/makefulltextfeed.php?url=" %))
       (apply hash-set)))

(defn feedbin-urls [data]
  (->> data
       (map :feed_url)
       (filter #(re-matches #"^http:\/\/ftr\.fivefilters\.org\/makefulltextfeed\.php\?url=https%3A%2F%2Fmedium.com%2Ffeed%2F.*" %))
       (apply hash-set)))

(defn feed-id [data url]
  (->> data
       (filter #(= url (:feed_url %)))
       first
       :id))

(defn unsubscribe [id feedbin-options]
  (let [{:keys [status headers body error]} @(http/delete (str "https://api.feedbin.com/v2/subscriptions/" id ".json") feedbin-options)]
    status))

(defn subscribe [url feedbin-options]
  (let [{:keys [status headers body error]} @(http/post "https://api.feedbin.com/v2/subscriptions.json" (assoc feedbin-options
                                                                                                          :body (json/encode {:feed_url url})))]
    status))

(defn env [x]
  "Returns configuration values from the environment, with a fallback to a .lein-env file in the
  classpath. This makes it possible to run without any configuration."
  (try
    (or (env/env x) (x (edn/read-string (slurp (io/resource ".lein-env")))))
    (catch java.lang.IllegalArgumentException e (env/env x))))

(def aws-gateway-options
  {:x-amazon-apigateway-integration
   {:responses {:default {:statusCode "200"
                          ;:responseParameters {"method.response.header.Content-Type" "integration.response.body.headers.X-Content-Type"}
                          :responseTemplates {"application/json" "$input.json('$.body')"}}}
    :requestTemplates { "application/json" (slurp (io/resource "bodymapping.vm")) }
    :uri (str "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/" (env/env :lambda-arn) "/invocations")
    :httpMethod "POST"
    :type "aws"}})

(def sync-resource
  (resource
    {:get
     (merge
       aws-gateway-options
       {:responses {200 {:schema schema/Any :description "My default response" :headers {"Content-Type" String}}}
        :parameters {:query-params {:token String :id String :username String :password String}}
        :summary "Render template with request map"
        :handler (fnk [[:query-params token id username password]]
                      (ok (let [medium-options {:oauth-token token}
                                feedbin-options {:basic-auth [username password] :insecure? true :headers {"Content-Type" "application/json"}}
                                medium (http/get (str "https://api.medium.com/v1/users/" id "/publications") medium-options)
                                feedbin (http/get "https://api.feedbin.com/v2/subscriptions.json" feedbin-options)
                                all-feedbin-feeds (json/parse-string (:body @feedbin) true)
                                medium-feeds (medium-urls (json/parse-string (:body @medium) true))
                                feedbin-feeds (feedbin-urls all-feedbin-feeds)
                                subscribe-to (set/difference medium-feeds feedbin-feeds)
                                unsubscribe-from (set/difference feedbin-feeds medium-feeds)]
                            {:existing medium-feeds
                             :token token
                             :id id
                             :username username
                             :password password
                             :subscribe (map #(subscribe % feedbin-options) subscribe-to)
                             :unsubscribe (map #(unsubscribe % feedbin-options) (->> unsubscribe-from (map #(feed-id all-feedbin-feeds %))))})))})}))

(def app
  (api
    {:swagger
     {:ui "/"
      :data {:info {:title "Medium-Feedbin Sync"
            :description "Get Medium subscriptions into Feedbin"}}
      :spec "/swagger.json"}}
      (context "/sync" [] sync-resource)))

(defhandler medium-feedreader.core.Lambda app {})
