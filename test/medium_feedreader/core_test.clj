(ns medium-feedreader.core-test
  (:require [clojure.test :refer :all]
            [medium-feedreader.core :refer :all]))

(deftest swagger
  (is (not (nil? app)))
  (is (not (nil? (app {:request-method :get, :uri "/swagger.json"}))))
  (spit "target/swagger.json" (slurp (:body (app {:request-method :get, :uri "/swagger.json"})))))
