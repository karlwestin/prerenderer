(ns prerenderer.server-test
  (:require [prerenderer.core :as prerenderer]))

(defn render-static [path]
  (str "static page: " path))

(defn render-data [path data]
  (str
    "dynamic page: "
    path
    " user data: "
    (.stringify js/JSON data)))

(defn render-and-send
  ([path send-to-browser]
    (send-to-browser (render-static path)))
  ([path data send-to-browser]
    (send-to-browser (render-data path data))))

(set! *main-cli-fn* (prerenderer/create render-and-send "Prerender"))
