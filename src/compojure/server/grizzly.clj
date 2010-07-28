;; Copyright (c) James Reeves. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure.server.grizzly
  "Clojure interface to start an embedded Grizzly server. To compile, use:
   ant -Dwith.grizzly"
  (:use compojure.control
        compojure.server.common)
  (:import javax.servlet.Servlet
           com.sun.grizzly.http.embed.GrizzlyWebServer
           com.sun.grizzly.http.servlet.ServletAdapter))

(defn #^ServletAdapter servlet-adapter
  "Wrap a servlet in a ServletAdapter object with a supplied set of parameters
  to be set on servlet init."
  [#^Servlet servlet & params]
  (let [adapter (new ServletAdapter servlet)
        params (partition 2 params)]
    (doseq [[key val] params]
      (.addInitParameter adapter (name key) (str val)))
    adapter))

(defn add-servlet!
  "Add a servlet to a Grizzly server. Servlets can be connected to a relative
  path or an absolute URL. Unlike the Jetty server, no Virtual Hosts
  are setup."
  [#^GrizzlyWebServer server url-or-path servlet]
  (let [[host path] (get-host-and-path url-or-path)
        #^ServletAdapter adapter (if (instance? ServletAdapter servlet)
                                   servlet
                                   ;; Otherwise, assume it's a servlet.
                                   (let [#^Servlet ss (cast Servlet servlet)]
                                     (ServletAdapter. ss)))]
    (.addGrizzlyAdapter server adapter (into-array [path]))))

(defn- #^GrizzlyWebServer create-server
  "Construct a Grizzly Server instance."
  [options servlets]
  (let [port     (options :port 80)
        server   (GrizzlyWebServer. (int port))
        servlets (partition 2 servlets)]
    (doseq [[url-or-path servlet] servlets]
      (add-servlet! server url-or-path servlet))
    server))

(defn #^GrizzlyWebServer grizzly-server
  "Create a new Grizzly HTTP server with the supplied options and servlets."
  [options & servlets]
  (server-with-options create-server options servlets))

(defmacro defserver
  "Shortcut for (def name (http-server args))"
  [name & args]
  `(def ~name (grizzly-server ~@args)))

(defn start "Start a HTTP server."
  [#^GrizzlyWebServer server]
  (.start server))

(defn stop  "Stop a HTTP server."
  [#^GrizzlyWebServer server]
  (.stop server))

(defn run-server
  "Create and start a new Grizzly HTTP server."
  [& server-args]
  (let [#^GrizzlyWebServer server (apply grizzly-server server-args)]
    (.start server)
    server))
