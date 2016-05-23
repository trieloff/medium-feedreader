(defproject medium-feedreader "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [http-kit "2.1.19"]
                 [less-awful-ssl "1.0.1"]
                 [ring/ring-codec "1.0.1"]
                 [environ "1.0.2"]
                 [metosin/compojure-api "1.1.2"]
                 [ring-aws-lambda-adapter "0.1.1"]
                 [cheshire "5.6.1"]]
  :ring {:handler medium-feedreader.core/app}
  :uberjar-name "server.jar"
  :resource-paths ["resources"
                   ".lein-env"] ; let's see if we can sneak the environment variables into the binary
  :plugins [[test2junit "1.1.2"]
            [lein-environ "1.0.2"]
            [lein-aws-api-gateway "1.10.68-1"]
            [lein-clj-lambda "0.4.1"]
            [lein-maven-s3-wagon "0.2.5"]]
  :api-gateway {;:api-id "c8sc0xfjf7"
                :swagger "target/swagger.json"}

  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit")
  :env {:aws-access-key #=(eval (System/getenv "AWS_ACCESS_KEY"))
        :lambda-arn #=(eval (System/getenv "LAMBDA_ARN"))
        :aws-secret-key #=(eval (System/getenv "AWS_SECRET_KEY"))}
  :lambda {"dev" [{:handler "medium-feedreader.core.Lambda"
                  :memory-size 512
                  :timeout 300
                  :function-name "mediumfeedreader-dev"
                  :region "us-east-1"
                  :s3 {:bucket "leinrepo"
                       :object-key "mediumfeedreader-dev.jar"}}]
         "production" [{:handler "medium-feedreader.core.Lambda"
                        :memory-size 512
                        :timeout 300
                        :function-name "mediumfeedreader-prod"
                        :region "us-east-1"
                        :s3 {:bucket "leinrepo"
                            :object-key "mediumfeedreader-release.jar"}}]}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]]
                   :plugins [[lein-ring "0.9.7"]]}
             :uberjar {:main medium-feedreader.core :aot :all}})
