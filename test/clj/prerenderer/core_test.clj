;;;; Copyright © 2015 Carousel Apps, Ltd. All rights reserved.

(ns prerenderer.core-test
  (:use clojure.test
        prerenderer.core)
  (:require [cljs.build.api :refer [build]]))

(defn build-cljs! []
  (build
    "test/cljs"
      {:output-dir "tmp/prerenderer-cljs"
       :output-to  "tmp/prerenderer-cljs/server-side.js"
       :optimizations :none
       :source-map true
       :pretty-print true
       :verbose    true
       :main       "prerenderer.server-test"
       :target     :nodejs}))

(deftest prerenderer-tests
  (let [build-product (build-cljs!)
        renderer (start! {:path "tmp/prerenderer-cljs/server-side.js"})]
    (println (render renderer "/dynamic-page" {} {:dynamic-data-hash 11}))
    (try
      (testing "rendering a static page"
          (is (re-find
                #"/static-test-page"
                (render renderer "/static-test-page" {}))))
      (testing "rendering a dynamic page page"
        (let [result (render renderer "/dynamic-page" {} {:dynamic-data-hash 11})]
          (is (re-find #"/dynamic-page" result))
          (is (re-find #"dynamic-data-hash" result))))
      ;; need to stop renderer for `lein test` to exit
      (finally (stop! renderer)))))
