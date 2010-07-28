;; Copyright (c) James Reeves. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure.http.request
  "Functions for pulling useful data out of a HTTP request map."
  (:use compojure.control
        compojure.encodings
        compojure.map-utils
        compojure.str-utils
        clojure.contrib.duck-streams
        clojure.contrib.str-utils)
  (:import java.net.URLDecoder
           java.io.InputStreamReader))

(defn- parse-params
  "Parse parameters from a string into a map."
  [param-string separator]
  (reduce
    (fn [param-map s]
      (if-let [[_ key val] (re-matches #"([^=]+)=(.*)" s)]
        (assoc-vec param-map
          (keyword (urldecode key))
          (urldecode (or val "")))
        param-map))
    {}
    (remove blank?
      (re-split separator param-string))))

(defn parse-query-params
  "Parse parameters from the query string."
  [request]
  (if-let [query (request :query-string)]
    (parse-params query #"&")))

(defn get-character-encoding
  "Get the character encoding, or use the default from duck-streams."
  [request]
  (or (request :character-encoding) *default-encoding*))

(defn- slurp-body
  "Slurp the request body into a string."
  [request]
  (let [encoding (get-character-encoding request)]
    (if-let [body (request :body)]
      (slurp* (InputStreamReader. body encoding)))))

(defn urlencoded-form?
  "Does a request have a urlencoded form?"
  [request]
  (if-let [type (:content-type request)]
    (.startsWith type "application/x-www-form-urlencoded")))

(defn parse-form-params
  "Parse urlencoded form parameters from the request body."
  [request]
  (if (urlencoded-form? request)
    (if-let [body (slurp-body request)]
      (parse-params body #"&"))))

(defn- get-merged-params
  "Get a map of all the parameters merged together."
  [request]
  (merge (:query-params request)
         (:form-params request)
         (:params request)))

(defn- assoc-func
  "Associate the result of a (func request) with a key on the request map."
  [request key func]
  (if (contains? request key)
    request
    (assoc request key (or (func request) {}))))

(defn assoc-params
  "Associate urlencoded parameters with a request. The following keys are added
  to the request map: :query-params, :form-params and :params."
  [request]
  (-> request
    (assoc-func :query-params parse-query-params)
    (assoc-func :form-params  parse-form-params)
    (assoc-func :params       get-merged-params)))

(defn with-request-params
  "Decorator that adds urlencoded parameters to the request map."
  [handler]
  (fn [request]
    (handler (assoc-params request))))

(defn parse-cookies
  "Pull out a map of cookies from a request map."
  [request]
  (if-let [cookies (get-in request [:headers "cookie"])]
    (parse-params cookies #";\s*")))

(defn assoc-cookies
  "Associate cookies with a request map."
  [request]
  (assoc-func request :cookies parse-cookies))

(defn with-cookies
  "Decorator that adds cookies to a request map."
  [handler]
  (fn [request]
    (handler (assoc-cookies request))))
