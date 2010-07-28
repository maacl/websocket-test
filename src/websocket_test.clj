;; trying to test jetty websockets in clojure
;; http://blogs.webtide.com/gregw/entry/jetty_websocket_server

;; To do basic test in chrome:
;; connect to host:8080
;; open java console
;; _ws=new WebSocket("ws://192.168.1.17:8080")
;; _ws.onmessage = function(m){console.log("Recieved websocket message: " + m.data);}
;; _ws.send('Hello from websocket!!', '')                            

(ns websockettest
  (:use [compojure])
  (:require clojure.contrib.str-utils2)
  (:import java.io.IOException
           ;;java.util.Set
           ;;java.util.concurrent.CopyOnWriteArraySet
           javax.servlet.RequestDispatcher
           (javax.servlet.http
            HttpServletRequest
            HttpServletResponse)
           org.eclipse.jetty.util.TypeUtil
           org.eclipse.jetty.util.log.Log
           (org.eclipse.jetty.websocket
            WebSocket
            WebSocketServlet)))


(def members (atom  #{})) ;; set of member objects

(def outbounds (atom {}))

(defn sendmsgs
  [frame data members]
  (loop [col members]
    (if (empty? col)
      nil
      (do (println "member: " (first col))
          (.sendMessage (last (first col)) frame data)
          (recur (rest col))))))
  
(defn make-chat-websock []
  (let [state (atom 0)
        obj (proxy [WebSocket] []
              (onConnect [outbound]
                         (swap! outbounds assoc this outbound)
                         (swap! members conj this))
              (onMessage [frame data]
                         (do 
                           (println "recieved: " data)
                           (sendmsgs frame data @outbounds)))
              (onDisconnect []
                            (swap! outbounds dissoc this)))]
    obj))

(defn web-sock-serv []
  (proxy [WebSocketServlet] [] 
    (doGet [request response]
           ;; (let [context  (proxy-super getServletContext)
           ;;       _ (println "Context: " context)
           ;;       _ (println "name" (proxy-super getServletName))
           ;;       _ (println "hekki" (.getNamedDispatcher context (proxy-super getServletName) ))])
           (.. (proxy-super getServletContext)
               (getNamedDispatcher (proxy-super getServletName))
               (forward request response)))
    (doWebSocketConnect [request response]
                           (make-chat-websock))))

(defroutes chat-viewer
  (GET "/*" (serve-file (str (clojure.contrib.str-utils2/butlast (. System getProperty "user.dir") 3) "public") (params :*))))

(run-server {:port 8090} "/*" (web-sock-serv)))

(run-server {:port 8081} "/*" (servlet chat-viewer))
