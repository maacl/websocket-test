;; Copyright (c) James Reeves. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns compojure.http.response
  "Parse a Compojure route return value into a HTTP response map."
  (:use clojure.contrib.def
        compojure.http.helpers)
  (:import clojure.lang.Fn
           clojure.lang.IPersistentVector
           java.util.Map
           clojure.lang.ISeq
           java.io.File
           java.io.InputStream
           java.net.URL
           clojure.lang.Keyword))

(defmulti update-response
  "Update a response with an object. The type of object determines how the
  response is updated."
  (fn [request reponse update]
    (class update)))

(defmethod update-response Integer
  [request response status]
  (assoc response :status status))

(defmethod update-response String
  [request response body]
  (let [headers (merge (:headers (content-type "text/html")) (:headers response))
        response (assoc response :headers headers)]
    (if (string? (:body response))
      (merge-with str response {:body body})
      (assoc response :body body))))

(defmethod update-response ISeq
  [request response sequence]
  (assoc response :body sequence))

(defmethod update-response File
  [request response file]
  (assoc response :body file))

(defmethod update-response InputStream
  [request response stream]
  (assoc response :body stream))

(defmethod update-response URL
  [request response url]
  (assoc response :body (.openStream url)))

(defmethod update-response IPersistentVector
  [request response updates]
  (reduce (partial update-response request) response updates))

(defmethod update-response Keyword
  [request response kw]
  (if (not= kw :next)
    (update-response request response (str kw))))

(defmethod update-response Fn
  [request response func]
  (update-response request response (func request)))

(defmethod update-response nil
  [request response _]
  response)

(defn- merge-map
  "Merges an inner map in 'from' into 'to'"
  [to key from]
  (merge-with merge to (select-keys from [key])))

(defn- merge-bodies
  "Merge the bodies in 'from' into 'to'."
  [to from]
  (let [from (select-keys from [:body])]
    (if (and (-> to :body string?) (-> from :body string?))
      (merge-with str to from)
      (merge to from))))

(defn- merge-rest
  "Merge everything but the headers, session and body."
  [to from]
  (merge to (dissoc from :headers :session :body)))

(defmethod update-response Map
  [request response update-map]
  (-> response
    (merge-map :headers update-map)
    (merge-map :session update-map)
    (merge-bodies update-map)
    (merge-rest update-map)))

(defvar default-response
  {:status 200, :headers {}}
  "Default HTTP response map.")

(defn create-response
  "Create a new response map from an update object, x."
  [request x]
  (update-response request default-response x))
