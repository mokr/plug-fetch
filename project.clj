(defproject net.clojars.mokr/plug-fetch "0.1.0-SNAPSHOT"
  :description "Simplify fetching of data from backend to frontend in a cljs app (experimental)"
  :url "https://github.com/mokr/plug-fetch"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[cljs-ajax "0.8.4" :scope "provided"]
                 [org.clojure/clojure "1.11.1" :scope "provided"]
                 [org.clojure/clojurescript "1.11.4" :scope "provided"]
                 [re-frame "1.2.0" :scope "provided"]]
  :repl-options {:init-ns plug-fetch.core})
