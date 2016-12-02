;;;; Copyright © 2015 Carousel Apps, Ltd. All rights reserved.

(ns prerenderer.core
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clj-http.client :as http])
  (:import java.lang.ProcessBuilder
           java.io.File))

(defn ensure-javascript-exists
  ([js-engine] (ensure-javascript-exists js-engine false))
  ([js-engine notify-about-file-appearing]
   (let [path (:path js-engine)
         working-directory (:working-directory js-engine ".")
         full-path (.getAbsolutePath (java.io.File. working-directory path))]
     (if-not (.exists (io/as-file full-path))
       (let [message (str "File " full-path " for prerendering is not present. Did you compile your JavaScript? 'lein cljsbuild auto' maybe?")]
         (if (:wait js-engine)
           (do
             (println message "Waiting until it appears...")
             (Thread/sleep 100)
             (recur js-engine true))
           (throw (Exception. message))))
       (when notify-about-file-appearing
         (println "File" full-path "appeared. Pfiuuu!"))))))

(defn read-port-file [js-engine]
  (slurp (:port-file js-engine)))

(defn blank-port-file! [js-engine]
  (spit (:port-file js-engine) ""))

(defmacro with-timeout
  "Execute body until millis time happened.
  http://stackoverflow.com/questions/6694530/executing-a-function-with-a-timeout/6697469#6697469"
  [millis & body]
  `(let [future# (future ~@body)]
     (try (.get future# ~millis java.util.concurrent.TimeUnit/MILLISECONDS)
          (catch java.util.concurrent.TimeoutException x#
            (do (future-cancel future#)
                nil)))))

(defn is-running? [js-engine]
  (and (not (nil? js-engine))
       (not (nil? (:process js-engine)))
       (.isAlive (:process js-engine))))

(defn get-port-number [js-engine]
  (or (with-timeout
        (:start-timeout js-engine)
        (loop [port-number (read-port-file js-engine)]
          (if (string/blank? port-number)
            (if (is-running? js-engine)
              (do (Thread/sleep 100)
                  (recur (read-port-file js-engine)))
              (throw (Exception. (str "While waiting for port number, process died: " (:path js-engine)))))
            port-number)))
      (throw (Exception. (str "Waited for " (:start-timeout js-engine) " for " (:path js-engine) " to start and report its port number but it timed out.")))))

(defn create-process-builder [js-engine]
  (doto (ProcessBuilder. ["node" (:path js-engine)
                          "--port-file" (:port-file js-engine)
                          "--default-ajax-host" (:default-ajax-host js-engine)
                          "--default-ajax-port" (str (:default-ajax-port js-engine))])
    .inheritIO
    (.directory (io/file (:working-directory js-engine)))))

(defn start! [options]
  (let [js-engine (merge {:path              nil
                          :process           nil
                          :default-ajax-host "localhost"
                          :default-ajax-port 3000
                          :port-file         (.getPath (doto (File/createTempFile (str "com.carouselapps.prerenderer-" *ns* "-") ".port")
                                                         .deleteOnExit))
                          :start-timeout     5000
                          :wait              false
                          :noop-when-stopped false
                          :working-directory "."}
                         options)]
    (if (nil? (:path js-engine))
      (throw (Exception. "Path should be specified when starting a pre-rendering engine.")))
    (if-not (.exists (io/as-file (:working-directory js-engine)))
      (throw (Exception. "Working directory should exist when starting a pre-rendering engine.")))
    (ensure-javascript-exists js-engine)
    (blank-port-file! js-engine)
    (let [process (.start (create-process-builder js-engine))
          js-engine (assoc js-engine :process process)]
      (assoc js-engine :port-number (get-port-number js-engine)))))

(defn stop! [js-engine]
  (.destroy (:process js-engine))
  js-engine)

(defn render
  ([js-engine url headers]
    (render js-engine url headers nil))
  ([js-engine url headers data]
    (if (is-running? js-engine)
      (let [url (str "http://localhost:" (:port-number js-engine) "/render?" (http/generate-query-string {:url url}))]
        (if (nil? data)
          (:body (http/get url {:headers headers}))
          (:body (http/post url {:headers headers :form-params data :content-type :json}))))
      (let [message (str "Prerendering JavaScript engine not running when attempting to render " url  ". JavaScript Engine: " js-engine)]
        (if (:noop-when-stopped js-engine)
          (println message)
          (throw (Exception. message)))))))
