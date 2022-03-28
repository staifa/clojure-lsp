(ns clojure-lsp.feature.clojuredocs
  "clojuredocs integration inspired on orchard implementation."
  (:require
   [clojure-lsp.http :as http]
   [clojure-lsp.settings :as settings]
   [clojure-lsp.shared :as shared]
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [lsp4clj.protocols.logger :as logger]))

(set! *warn-on-reflection* true)

(def ^:private clojuredocs-edn-file-url
  "https://github.com/clojure-emacs/clojuredocs-export-edn/raw/master/exports/export.compact.edn")

(defn refresh-cache! [{:keys [db]}]
  (when (and (settings/get db [:hover :clojuredocs] true)
             (not (-> @db :clojuredocs :refreshing?)))
    (logger/info "Refreshing clojuredocs cache...")
    (swap! db assoc-in [:clojuredocs :refreshing?] true)
    (shared/logging-time
      "Refreshing clojuredocs cache took %s secs."
      (try
        (let [;; connection check not to wait too long
              [downloadable? conn-ex] (http/test-remote-url! clojuredocs-edn-file-url)]
          (if (not downloadable?)
            (logger/error "Could not refresh clojuredocs." conn-ex)
            (swap! db assoc :clojuredocs {:cache (-> clojuredocs-edn-file-url
                                                     slurp
                                                     edn/read-string)})))
        (catch Exception e
          (logger/error "Error refreshing clojuredocs information." e)
          nil)
        (finally
          (swap! db assoc-in [:clojuredocs :refreshing?] false))))))

(defn find-docs-for [sym-name sym-ns {:keys [db] :as components}]
  (when sym-ns
    (let [full-keyword (keyword (str sym-ns) (str sym-name))]
      (if-let [cache (-> @db :clojuredocs :cache)]
        (get cache full-keyword)
        (do
          (async/go
            (refresh-cache! components))
          nil)))))

(defn find-hover-docs-for [sym-name sym-ns {:keys [db] :as components}]
  (when (settings/get db [:hover :clojuredocs] true)
    (find-docs-for sym-name sym-ns components)))
