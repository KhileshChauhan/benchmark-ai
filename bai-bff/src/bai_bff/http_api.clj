;;  Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
;;
;;  Licensed under the Apache License, Version 2.0 (the "License").
;;  You may not use this file except in compliance with the License.
;;  A copy of the License is located at
;;
;;      http://www.apache.org/licenses/LICENSE-2.0
;;
;;  or in the "license" file accompanying this file. This file is distributed
;;  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
;;  express or implied. See the License for the specific language governing
;;  permissions and limitations under the License.
(ns bai-bff.http-api
  (:require [bai-bff.core :refer :all]
            [bai-bff.events :as events]
            [bai-bff.services.eventbus :as eventbus]
            [bai-bff.utils.persistence :as db]
            [clojure.pprint :refer :all]
            [clojure.java.io :as io]
            [ring.adapter.jetty :refer :all]
            [ring.util.response :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.middleware :refer [wrap-canonical-redirect]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [clojure.core.async :as a :refer [>!!]]))

;;----
;; To post a descriptor file do the following at the command line
;; %>curl --data-binary "@testfile.toml" http://localhost:8080/api/job/descriptor?name=gavin

;;----------------------
;; Action Functions
;;----------------------

(defn dispatch-submit-job [request body]
  (try
    (if-let [script-file-map (some-> request :params :script)]
      (eventbus/scripts->s3 script-file-map))
    (let [message-body (json/parse-string body true)]
      (log/debug "Printing request")
      (log/debug request)
      (log/debug "message body is now an event...")
      (let [event (events/message->submit-descriptor-event request message-body)
            status-event (partial events/status-event event)]
        (log/debug event)
        (log/info (json/generate-string event {:pretty true}))
        (log/trace "Storing submission")
        (db/save-client-job event)
        (>!! @eventbus/send-event-channel-atom [(status-event :bai-bff.events/succeeded (str "Submission has been successfully received..."))])
        (>!! @eventbus/send-event-channel-atom [event])
        (response (:action_id event))))
    (catch Exception e
      (log/error "Could Not Parse Descriptor Input:" (.getMessage e))
      (bad-request (str "Could Not Parse Submitted Descriptor: " (.getMessage e))))))

(defn dispatch-delete-job [request body action-id]
  (try
    (let [body-string (slurp body)]
      (log/debug "Printing request")
      (log/debug request)
      (log/debug "Body received is")
      (log/debug body-string)
      (log/debug "message body is now an event...")
      (let [event (events/message->cmd-event
                   request
                   (json/parse-string body-string true))
            status-event (partial events/status-event event)]
        (log/debug event)
        (log/info (json/generate-string event {:pretty true}))
        (>!! @eventbus/send-event-channel-atom [(status-event :bai-bff.events/pending (str "Action received, dispatching delete for <"action-id">"))])
        (>!! @eventbus/send-event-channel-atom [event])
        (response (:action_id event))))
    (catch Exception e
      (log/error "Could Not Parse Input" (.getMessage e))
      (bad-request (str "Could Not Parse Submitted Command " (.getMessage e))))))

;;----------------------
;; Misc Helper functions...
;;----------------------
;; Handle post processing "presentation"
(defn post-proc-results [results]
  (cond
    (nil? results) {:status 404}
    (empty? results) {:status 404}
    :else (response results)))


;;----------------------
;; REST API routing...
;;----------------------
(defroutes info-routes
  (GET "/" req
       (str "<hr><CENTER><h1>Welcome To The Anubis (BFF) Service (v"VERSION")</h1><a href=\"https://github.com/awslabs/benchmark-ai/\">https://github.com/awslabs/benchmark-ai</a></CENTER><hr><p>")))

;; Kubernetes Liveliness and Readiness endpoints
(defroutes k8s-routes
  (GET "/ready" req
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body "sure am ready"})
  (GET "/lively" req
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body "sure am lively"}))

;; API for BFF to post and query data
(defroutes api-routes
  (GET "/api" []
       (str "<hr><CENTER><h1> Anubis (BFF) HTTP Service API (v"VERSION")</h1><a href=\"http://foobar.com/api\">docs</a></CENTER><hr>"))
  (context "/api/tools" []
           (defroutes tool-routes
             (GET "/:toolname" [toolname] (slurp (io/resource toolname)))))
  (context "/api/job" []
           (defroutes job-routes
             (GET  "/script/:filename" [filename] (response (if (eventbus/has-file? filename) (str "true") (str "false"))))
             (GET  "/results/:client-id/:action-id" [client-id action-id] (response (eventbus/get-job-results client-id action-id)))
             (POST "/" {body :body :as request} (post-proc-results (log/info (pprint request)) #_(create-job body)));TODO - implement me
             (POST "/descriptor" {{body :submit-event} :params :as request} (dispatch-submit-job request body))
             (context "/:client-id" [client-id]
                      (defroutes client-routes
                        (GET    "/" {{:keys[since] :or {since "0"}} :params :as req} (post-proc-results (eventbus/get-client-jobs client-id since)))
                        (DELETE "/" [] (post-proc-results (log/info "delete-client-jobs... [NOT]") #_(delete-job action-id))))
                      (context "/:action-id" [action-id]
                               (defroutes action-routes
                                 (GET    "/" {{:keys[since] :or {since "0"}} :params :as req} (post-proc-results (eventbus/get-client-job-status-for-action client-id action-id since)))
                                 (DELETE "/" {body :body :as request} (dispatch-delete-job request body action-id))))))) ;
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(def core-routes
  (routes info-routes k8s-routes api-routes))

(defn wrap-with-service-version-header [handler version]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "X-Service-Version"] version))))

(defn create-application-routes[]
  (-> #'core-routes
      (wrap-with-service-version-header VERSION)
      (wrap-reload)
      (wrap-canonical-redirect)
      (wrap-json-response)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-keyword-params)
      (wrap-params)
      (wrap-multipart-params)
      (wrap-json-params)))
