(ns bai-client.app
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :refer :all]
            [clojure.string :as string]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.java.shell :only [sh]]
            [clojure.data.codec.base64 :as b64]
            [cheshire.core :as json]
            [digest]
            [environ.core :refer [env]]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [toml.core :as toml])
  (:import (java.net InetAddress))
  (:gen-class))

(defonce version "v0.0.1")
(log/set-level! (keyword (string/trim ^String(env :logging-level "warn"))))

;; ------
;; Utility Functions...
;; ------

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn gen-submit-event []
  (log/trace "generating submission event...")
  (let [event {:message_id (uuid)
               :client_id ""
               :client_version ""
               :client_sha1 ""
               :date ""
               :visted [{:svc ""
                         :tstamp ""
                         :version ""}]
               :payload {:toml {:descriptor_filename ""
                                :sha1 ""
                                :doc ""
                                }}}]
    event))

;; ------
;; Actions
;; ------

(defn cancel
  "Send a cancel event for the given action, provided that the target of the cancellation hasn't already been completed"
  [options]
  (log/trace "cancel called with: "options))

(defn inspect
  "Shows the full body of the event associated with the provided id"
  [options]
  (log/trace "inspect called with: "options))

(defn register
  "Registers (sets) the Benchmark AI (Anubis) network endpoint for his program - hostname and port"
  [options]
  (log/trace "register called with: "options))

(defn show
  "Shows more detailed information for the given directive"
  [options]
  (log/trace "show called with: "options))

(defn status
  "Gives the status of submitted actions"
  [options]
  (log/trace "status called with: "options))

(defn submit
  "Submits the specified descriptor input to Benchmark AI (Anubis) to execute"
  [options]
  (log/trace "submit called with: "options)
  (let [cli-options [["-f" "--filename <descriptor file>" "Path to TOML Descriptor file"]
                     ["-h" "--help" "This message"]]
        {:keys [options arguments errors summary] :as all} (parse-opts options cli-options :in-order true)
        filename (:filename options)
        event (gen-submit-event)]
    (log/trace (str "parsed options for submit:\n"all))
    (if (or (empty? options) (:help options))
      (do
        (println)
        (println summary)
        (println)
        (System/exit 0)))

    (printf "Loading descriptor file: %s\n" filename)
    ;TODO: fill in the payload section of the event
    ))

(defn sync!
  "Synchronize the local datastore with the state in Anubis"
  [options]
  (log/trace "sync! called with: "options))

(defn validate
  "Validate the syntax of the given descriptor file"
  [options]
  (log/trace "validate called with: "options))


;; ---------------------

(def cli-options
  [;; First three strings describe a short-option, long-option with optional
   ;; example argument description, and a description. All three are optional
   ;; and positional.
   ["-P" "--port <PORT>" "Anubis service port number"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--hostname <HOSTNAME>" "Anubis service hostname (or IP)"
    :default (InetAddress/getByName "localhost")
    ;; Specify a string to output in the default column in the options summary
    ;; if the default value's string representation is very ugly
    :default-desc "localhost"
    :parse-fn #(InetAddress/getByName %)]
   ;; If no required argument description is given, the option is assumed to
   ;; be a boolean option defaulting to nil
   ["-v" "Verbosity level (may be specified multiple times to increase value)"
    ;; If no long-option is specified, an option :id must be given
    :id :verbosity
    :default 0
    ;; Use :update-fn to create non-idempotent options (:default is applied first)
    :update-fn inc]
   ["-h" "--help" "This message"]])

;; The :default values are applied first to options. Sometimes you might want
;; to apply default values after parsing is complete, or specifically to
;; compute a default value based on other option values in the map. For those
;; situations, you can use :default-fn to specify a function that is called
;; for any options that do not have a value after parsing is complete, and
;; which is passed the complete, parsed option map as it's single argument.
;; :default-fn (constantly 42) is effectively the same as :default 42 unless
;; you have a non-idempotent option (with :update-fn or :assoc-fn) -- in which
;; case any :default value is used as the initial option value rather than nil,
;; and :default-fn will be called to compute the final option value if none was
;; given on the command-line (thus, :default-fn can override :default)

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: bai-client [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  cancel    Cancel a submitted action"
        "  inspect   Inspect the details of an event"
        "  register  Register an Anubis endpoint"
        "  show      Shows more detailed information for the given directive"
        "  status    Gives the status of submitted actions"
        "  submit    Submits the specified descriptor input to Benchmark AI (Anubis) to execute"
        "  sync      Synchronize the local datastore with the state in Anubis"
        "  validate  Validate the syntax of the given descriptor file"
        ""
        "Please refer to the site https://github.com/MXNetEdge/benchmark-ai/tree/bai-client/bai-client  for more information."
        ""]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary] :as all} (parse-opts args cli-options :in-order true)]
    (log/trace (str "parsed options:\n"all))

    ;; #{:trace :debug :info :warn :error :fatal :report}
    (condp = (:verbosity options)
      0 (log/set-level! :warn)
      1 (log/set-level! :info)
      2 (log/set-level! :debug)
        (log/set-level! :trace))

    (cond
      (:help options) {:exit-message (usage summary) :ok? true}
      errors          {:exit-message (error-msg errors)}
      (#{"cancel"
         "inspect"
         "register"
         "show"
         "status"
         "submit"
         "sync"
         "validate"} (first arguments)) {:action (first arguments) :options (rest arguments)}
      :else           {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn initialize
  "Make sure that we have our environment and directory set up..."
  [])

(defn -main [& args]
  (initialize)
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "cancel"   (cancel options)
        "inspect"  (status options)
        "register" (inspect options)
        "show"     (show options)
        "status"   (status options)
        "submit"   (submit options)
        "sync"     (sync! options)
        "validate" (validate options)))))