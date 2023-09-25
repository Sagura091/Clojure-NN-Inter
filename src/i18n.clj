(ns i18n
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clj-http.client :as client]
            [dynne.sampled-sound :as sound])
  (:import (java.io FileOutputStream)))


(def my-dictionary
  (atom
    (if-let [resource-url (io/resource "dictionary.edn")]
      (-> resource-url slurp edn/read-string)
      {:demo {}})))  ;; default value if the file doesn't exist




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

(defn text-to-speech-api [voice-id xi-api-key text lang]
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
      (write-bytes-to-file (str lang + "output-audio.mp3" ) response-body)
      ;; Handle error
      (let [error-data (json/parse-string (String. response-body) true)]
        (println "Error:" error-data)))))

;; Replace these values with actual values
(def voice-id "A1S4daMLgRDQERHyU131")
(def xi-api-key "8ce33055a4861e9c9bf4881f2384957e")
(def text "Your text to convert to speech")



(defn translate
  [text target-lang]
  (let [api-key "d83ac8a4-9cfc-408e-89b3-7158a69d14e2"
        url "https://api.deepl.com/v2/translate"
        params {:text text
                :target_lang (clojure.string/upper-case (name target-lang))
                :auth_key api-key}
        response (client/post url {:form-params params
                                   :content-type "application/x-www-form-urlencoded"
                                   :decompress-body true})]
    (log/info "Full Response body:" (:body response))
    (if (<= 200 (:status response) 299)
      (let [parsed-body (json/parse-string (:body response) true)]
        (log/info "Parsed JSON body:" parsed-body)  ;; Log the parsed JSON body
        (-> parsed-body :translations first :text))  ;; Correct extraction of :text
      (do
        (log/error "Translation request failed with status" (:status response))
        (log/error "Response body:" (:body response))
        nil))))

(defn keyword-name [main-key k]
  (keyword (str (name main-key) "/" (name k))))


(defn update-dictionary
  [key subkey lang new-translation]
  (let [old-dictionary @my-dictionary
        new-dictionary (assoc-in old-dictionary [key lang subkey] new-translation)]
    (if (= old-dictionary new-dictionary)
      (log/info "No new translation added.")
      (do
        (reset! my-dictionary new-dictionary)
        (spit (io/resource "dictionary.edn")
              (with-out-str (clojure.pprint/pprint @my-dictionary)))
        (log/info "New translation added. Updated dictionary:" @my-dictionary)))))

(defn translate-and-update
  [key subkey lang]
  (let [english-text (get-in @my-dictionary [key :en  subkey])
        translated-text (translate english-text lang)]
    (println translated-text)
    (text-to-speech-api voice-id xi-api-key translated-text lang) 
    (log/info "Translation response:" translated-text)
    (update-dictionary key subkey lang translated-text)))

(defn add-and-translate
  [key subkey english-text languages]
  (log/info "Adding and translating" key subkey english-text languages)
  (update-dictionary key subkey :en english-text)
  (doseq [lang languages]
    (translate-and-update key subkey lang)
    (Thread/sleep 1000)))

(defn -main
  [& args]
  (add-and-translate :demo :Button-Toggle "GUI Page Translation" [:fr :pl :es :ja :fi :de]))
