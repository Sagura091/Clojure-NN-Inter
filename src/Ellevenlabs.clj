(ns Ellevenlabs
  (:require [clj-http.client :as client]
            [cheshire.core :as json])
  (:import (java.io FileOutputStream)))

(defn write-bytes-to-file [file-path bytes]
  (with-open [fos (FileOutputStream. file-path)]
    (.write fos bytes)))


(defn fetch-models [xi-api-key]
  (let [url "https://api.elevenlabs.io/v1/models"
        headers {:accept "application/json"
                 :xi-api-key xi-api-key}
        response (client/get url {:headers headers})
        response-body (:body response)]
    (if (= (:status response) 200)
      (let [models (json/parse-string response-body true)]
        (println "Available models:" models))
      (let [error-data (json/parse-string response-body true)]
        (println "Error fetching models:" error-data)))))

(defn text-to-speech-api [voice-id xi-api-key text]
  (let [url (str "https://api.elevenlabs.io/v1/text-to-speech/" voice-id)
        headers {:accept "audio/mpeg"
                 :xi-api-key xi-api-key
                 :content-type "application/json"}
        request-body {:text text
                      :model_id "eleven_multilingual_v2"
                      :voice_settings {:stability 0.5
                                       :similarity_boost 0.5}}
        response (client/post url
                              {:headers headers
                               :body (json/generate-string request-body)
                               :as :byte-array}) ; ensure the response is a byte array
        response-body (:body response)]
    (if (= (:status response) 200)
      ;; Handle success, e.g., save the audio file
      (write-bytes-to-file "output-audio.mp3" response-body)
      ;; Handle error
      (let [error-data (json/parse-string (String. response-body) true)]
        (println "Error:" error-data)))))

;; Replace these values with actual values
(def voice-id "xqC7feGiCe6tQIFn3lN5")
(def xi-api-key "8ce33055a4861e9c9bf4881f2384957e")
(def text "Your text to convert to speech")

(defn -main
  [& args]
  (fetch-models xi-api-key)
  (text-to-speech-api voice-id xi-api-key text))
