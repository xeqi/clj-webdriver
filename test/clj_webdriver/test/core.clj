(ns clj-webdriver.test.core
  (:use [clj-webdriver core util window-handle wait options form-helpers])
  (:use [ring.adapter.jetty :only [run-jetty]]
        clojure.test)  
  (:require [clj-webdriver.test.example-app.core :as web-app]
            [clj-webdriver.cache :as cache]
            [clojure.tools.logging :as log])
  (:import [clj_webdriver.driver.Driver]
           [org.openqa.selenium TimeoutException]))

;; ## Setup ##
(def test-port 5744)
(def test-host "localhost")
(def test-base-url (str "http://" test-host ":" test-port "/"))

(declare ^{:dynamic true} dr)
(declare ^{:dynamic true} wdr)
(declare ^{:dynamic true} dr-plain)

(defn create-browser-fixtures [f]
  (binding [dr (to (new-driver :firefox {:strategy :basic,
                                    :args [],
                                    :include [ (fn [element] (= (attribute element :class) "external"))
                                               {:css "ol#pages"}]}) test-base-url)
            wdr (start :firefox test-base-url :webdriver)
            dr-plain (to (new-driver :firefox) test-base-url)]
    (f)))


(defn start-server [f]
  (loop [server (run-jetty #'web-app/routes {:port test-port, :join? false})]
    (if (.isStarted server)
      (do
        (f)
        (.stop server))
      (recur server))))

(defn reset-browser-fixture
  [f]
  (to dr test-base-url)
  (to dr-plain test-base-url)
  (to wdr test-base-url)
  (f))

(defn quit-browser-fixture
  [f]
  (f)
  (quit dr)
  (quit dr-plain)
  (quit wdr))

(defn seed-driver-cache
  [f]
  (cache/seed dr {:url (current-url dr), {:query [:foo]} "bar"})
  (f))

(use-fixtures :once start-server create-browser-fixtures quit-browser-fixture)
(use-fixtures :each reset-browser-fixture seed-driver-cache)



;; ## Cache-Based Tests ##
(deftest test-browser-basics
  (is (= clj_webdriver.driver.Driver (class dr)))
  (is (= test-base-url (current-url dr)))
  (is (= "Ministache" (title dr)))
  (is (boolean (re-find #"(?i)<!DOCTYPE html>" (page-source dr)))))

(deftest test-back-forward
  (-> dr
      (find-it :a {:text "example form"})
      click)
  (is (= (str test-base-url "example-form") (current-url dr)))
  (back dr)
  (is (= test-base-url (current-url dr)))
  (forward dr)
  (is (= (str test-base-url "example-form") (current-url dr))))

(deftest test-to
  (to dr (str test-base-url "example-form"))
  (is (= (str test-base-url "example-form") (current-url dr)))
  (is (= "Ministache" (title dr))))

(deftest test-bys
  (-> dr
      (find-it :a {:text "example form"})
      click)
  (is (= "first_name"
         (attribute (find-element dr (by-id "first_name")) :id)))
  (is (= "home"
         (text (find-element dr (by-link-text "home")))))
  (is (= "example form"
         (text (find-element dr (by-partial-link-text "example")))))
  (is (= "first_name"
         (attribute (find-element dr (by-name "first_name")) :id)))
  (is (= "home"
         (text (find-element dr (by-tag-name "a")))))
  (is (= "home"
         (text (find-element dr (by-xpath "//a[text()='home']")))))
  (is (= "home"
         (text (find-element dr (by-class-name "menu-item")))))
  (is (= "home"
         (text (find-element dr (by-css-selector "#footer a.menu-item"))))))

(deftest test-find*
  (is (= "Moustache"
         (text (nth (find-them dr :a) 1))))
  (is (= "Moustache"
         (text (find-it dr {:class "external"}))))
  (is (= "https://github.com/cgrand/moustache"
         (attribute (find-it dr {:text "Moustache"}) "href")))
  (is (= "Moustache"
         (text (find-it dr :a {:class #"exter"}))))
  (is (= "Moustache"
         (text (find-it dr :a {:text #"Mous"}))))
  (is (= "Moustache"
         (text (find-it dr :a {:class "external", :href #"github"}))))
  (is (= "Moustache"
         (text (find-it dr :a {:class #"exter", :href #"github"}))))
  (is (= "Moustache"
         (text (find-it dr [:div {:id "content"}, :a {:class "external"}]))))
  (is (= "Moustache"
         (text (find-it dr [:div {:id "content"}, :a {:class #"exter"}]))))
  (is (= "Moustache"
         (text (find-it dr [:div {:id "content"}, :a {:href #"github"}]))))
  (is (= "home"
         (text (find-it dr [:* {:id "footer"}, :a {}]))))
  (is (= 8
         (count (find-them dr :a))))
  (is (= 3
         (count (find-them dr {:class #"-item"}))))
  (is (= 3
         (count (find-them dr :a {:class #"-item"}))))
  (is (= 1
         (count (find-them dr :a {:text #"hom"}))))
  (is (= 1
         (count (find-them dr :a {:text #"(?i)HOM"}))))
  (is (= 2
         (count (find-them dr :a {:class #"exter", :href #"github"}))))
  (is (= 3
         (count (find-them dr [:* {:id "footer"}, :a {}]))))
  (is (= 2
         (count (find-them dr [:div {:id "content"}, :a {:class #"exter"}]))))
  (-> dr
      (find-it :a {:text "example form"})
      click)
  (is (= "first_name"
         (attribute (find-it dr {:type "text"}) "id")))
  (is (= "first_name"
         (attribute (find-it dr :input {:type "text"}) "id")))
  (is (= "first_name"
         (attribute (find-it dr :input {:type "text", :name "first_name"}) "id")))
  (is (= "first_name"
         (attribute (find-it dr :input {:type "text", :name #"first_"}) "id")))
  (is (= "last_name"
         (attribute (find-it dr :input {:type "text", :name #"last_"}) "id")))
  (is (= "Smith"
         (attribute (find-it dr :input {:type "text", :name #"last_"}) "value")))
  (is (= "Smith"
         (attribute (find-it dr :input {:type "text", :name #"last_"}) "value")))
  (is (= "Smith"
         (attribute (find-it dr [:div {:id "content"}, :input {:name #"last_"}]) "value")))
  (back dr) ;; get back to home page
  (is (-> dr
        (find-it :a)
        exists?))
  (is (not
       (-> dr
           (find-it :area)
           exists?)))
  (is (nil?
       (-> dr
           (find-it :area)
           exists?)))
  (is (-> dr 
          (find-it :a {:text "Moustache"})
          visible?))
  (is (-> dr 
          (find-it :a {:text "Moustache"})
          displayed?))
  (is (-> dr
          (find-it :a {:text "Moustache"})
          present?))
  (is (not
       (-> dr
           (find-it :a)
           visible?)))
  (is (not
       (-> dr 
           (find-it :a)
           displayed?)))
  (is (not
       (-> dr
           (find-it :a)
           present?)))
  (is (thrown? org.openqa.selenium.NoSuchElementException
               (find-it dr :area))))

;; Default wrap for strings is double quotes
(deftest test-xpath-quote-handling
  (is (find-it dr {:text "File's Name"})))

(deftest test-form-elements
  (to dr (str test-base-url "example-form"))
  ;; Clear element
  (-> dr
      (find-it [:form {:id "example_form"}, :input {:name #"last_"}])
      clear)
  (is (= ""
         (value (find-it dr [:form {:id "example_form"}, :input {:name #"last_"}]))))
  ;; Radio buttons
  (is (= true
         (selected? (find-it dr :input {:type "radio", :value "male"}))))
  (-> dr
      (find-it :input {:type "radio", :value "female"})
      select)
  (is (= true
         (selected? (find-it dr :input {:type "radio", :value "female"}))))
  (-> dr
      (find-it :radio {:value "male"})
      select)
  (is (= true
         (selected? (find-it dr :input {:type "radio", :value "male"}))))
  ;; Checkboxes
  (is (= false
         (selected? (find-it dr :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> dr
      (find-it :input {:type "checkbox", :name #"(?i)clojure"})
      toggle)
  (is (= true
         (selected? (find-it dr :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> dr
      (find-it :checkbox {:name #"(?i)clojure"})
      click)
  (is (= false
         (selected? (find-it dr :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> dr
      (find-it :checkbox {:type "checkbox", :name #"(?i)clojure"})
      select)
  (is (= true
         (selected? (find-it dr :input {:type "checkbox", :name #"(?i)clojure"}))))
  ;; Text fields
  (is (= "true"
         (attribute (find-it dr :input {:type "text", :value "Testing!"})
                    "readonly")))
  (-> dr
      (find-it :input {:id "first_name"})
      (input-text "foobar"))
  (is (= "foobar"
         (value (find-it dr :input {:id "first_name"}))))
  (-> dr
      (find-it :textfield {:id "first_name"})
      clear
      (input-text "clojurian"))
  (is (= "clojurian"
         (value (find-it dr :textfield {:id "first_name"})))))

(deftest test-form-helpers
  (to dr (str test-base-url "example-form"))
  (quick-fill dr
              [{"first_name" clear}
               {"first_name" "Richard"}
               {{:id "last_name"} clear}
               {{:id "last_name"} "Hickey"}
               {{:name "bio"} clear}
               {{:name "bio"} #(input-text % "Creator of Clojure")}
               {{:tag-name "input", :type "radio", :value "female"} click}
               {{:css "select#countries"} #(select-by-value % "france")}])
  (is (= "Richard"
         (value (find-it dr :input {:id "first_name"}))))
  (is (= "Hickey"
         (value (find-it dr :input {:id "last_name"}))))
  (is (= "Creator of Clojure"
         (value (find-it dr :input {:name "bio"}))))
  (is (selected?
       (find-it dr :input {:type "radio", :value "female"})))
  (is (selected?
       (find-it dr :option {:value "france"}))))

(deftest test-window-handling
  (is (= 1
         (count (window-handles dr))))
  (is (= "Ministache"
         (:title (window-handle dr))))
  (-> dr
      (find-it :a {:text "is amazing!"})
      click)
  (is (= "Ministache"
         (:title (window-handle dr))))
  (is (= 2
         (count (window-handles dr))))
  (switch-to-window dr (second (window-handles dr)))
  (is (= (str test-base-url "clojure")
         (:url (window-handle dr))))
  (switch-to-other-window dr)
  (is (= test-base-url
         (:url (window-handle dr))))
  (-> dr
      (switch-to-window (find-it dr :window {:url (str test-base-url "clojure")})))
  (close dr)
  (is (= test-base-url
         (:url (window-handle dr)))))

(deftest wait-until-should-wait-for-condition
  (is (= "Ministache" (title dr)))
  (-> dr
    (execute-script "setTimeout(function () { window.document.title = \"asdf\"}, 3000)")
    (wait-until (fn [d] (= "asdf" (title d)))))
  (is (= "asdf" (title dr))))

(deftest wait-until-should-throw-on-timeout
  (is (thrown? TimeoutException
               (-> dr
                 (execute-script "setTimeout(function () { window.document.title = \"test\"}, 6000)")
                 (wait-until (fn [d] (= "test" (title d))))))))

(deftest wait-until-should-allow-timeout-argument
  (is (thrown? TimeoutException
               (-> dr
                   (execute-script "setTimeout(function () { window.document.title = \"test\"}, 10000)")
                   (wait-until (fn [d] (= "test" (title d))) 1000)))))

(deftest implicit-wait-should-cause-find-to-wait
  (-> dr
      (implicit-wait 3000)
      (execute-script "setTimeout(function () { window.document.body.innerHTML = \"<div id='test'>hi!</div>\"}, 1000)"))
  (is (= "test"
         (attribute (find-element dr (by-id "test")) :id))))

;; Not sure how we'll test that flash in fact flashes,
;; but at least this will catch changing API's
(deftest test-flash-helper
  (-> dr
      (find-it :a {:text "Moustache"})
      flash))

;; Caching
(deftest test-cache-initialization
  (is (cache/cache-enabled? dr)))

(deftest test-cache-insert
  ;; insert was used to seed the data in the test fixture; test now for presence
  (is (= (get @(:element-cache dr) {:query [:foo]}) "bar"))
  (is (nil? (get @(:element-cache dr) :wowza))))

(deftest test-in-cache?
  (is (cache/in-cache? dr {:query [:foo]}))
  (is (not (cache/in-cache? dr :wowza))))

(deftest test-cache-retrieve
  (is (= (cache/retrieve dr :foo) "bar"))
  (is (nil? (cache/retrieve dr :wowza))))

(deftest test-cache-delete
  (cache/insert dr {:query [:alpha]} "beta")
  (is (= (cache/retrieve dr :alpha) "beta"))
  (cache/delete dr :alpha)
  (is (nil? (cache/retrieve dr :alpha))))

(deftest test-cache-seed
  (cache/seed dr {{:query [:foo]} "clojure"})
  (is (= (cache/retrieve dr :foo) "clojure"))
  (cache/seed dr)
  (is (= @(:element-cache dr) {:url (current-url dr)})))

(deftest test-cacheable?
  ;; assume at test-base-url
  (is (cache/cacheable? dr (find-it dr :a {:class "external"})))
  (is (not (cache/cacheable? dr {:class "external"})))
  (is (cache/cacheable? dr {:css "ol#pages"}))
  (is (not (cache/cacheable? dr :table)))
  (is (not (cache/cacheable? dr {:css "#pages"}))))

(deftest test-cache-excludes
  ;; includes are tested by default
  (let [temp-dr (to (new-driver :firefox {:strategy :basic,
                                           :args [],
                                           :exclude [ (fn [element] (= (attribute element :class) "external")),
                                                      {:css "ol#pages"}]}) test-base-url)]
    (is (cache/cacheable? temp-dr (find-it temp-dr :table)))
    (is (cache/cacheable? temp-dr (find-it temp-dr {:css "#pages"})))
    (is (not (cache/cacheable? temp-dr (find-it temp-dr :a {:class "external"}))))
    (is (not (cache/cacheable? temp-dr {:css "ol#pages"})))
    (quit temp-dr)))




;; ## Tests (sans cache) ##
(deftest plain-test-browser-basics
  (is (= clj_webdriver.driver.Driver (class dr-plain)))
  (is (= test-base-url (current-url dr-plain)))
  (is (= "Ministache" (title dr-plain)))
  (is (boolean (re-find #"(?i)<!DOCTYPE html>" (page-source dr-plain)))))

(deftest plain-test-back-forward
  (-> dr-plain
      (find-it :a {:text "example form"})
      click)
  (is (= (str test-base-url "example-form") (current-url dr-plain)))
  (back dr-plain)
  (is (= test-base-url (current-url dr-plain)))
  (forward dr-plain)
  (is (= (str test-base-url "example-form") (current-url dr-plain))))

(deftest plain-test-to
  (to dr-plain (str test-base-url "example-form"))
  (is (= (str test-base-url "example-form") (current-url dr-plain)))
  (is (= "Ministache" (title dr-plain))))

(deftest plain-test-bys
  (-> dr-plain
      (find-it :a {:text "example form"})
      click)
  (is (= "first_name"
         (attribute (find-element dr-plain (by-id "first_name")) :id)))
  (is (= "home"
         (text (find-element dr-plain (by-link-text "home")))))
  (is (= "example form"
         (text (find-element dr-plain (by-partial-link-text "example")))))
  (is (= "first_name"
         (attribute (find-element dr-plain (by-name "first_name")) :id)))
  (is (= "home"
         (text (find-element dr-plain (by-tag-name "a")))))
  (is (= "home"
         (text (find-element dr-plain (by-xpath "//a[text()='home']")))))
  (is (= "home"
         (text (find-element dr-plain (by-class-name "menu-item")))))
  (is (= "home"
         (text (find-element dr-plain (by-css-selector "#footer a.menu-item"))))))

(deftest plain-test-find*
  (is (= "Moustache"
         (text (nth (find-them dr-plain :a) 1))))
  (is (= "Moustache"
         (text (find-it dr-plain {:class "external"}))))
  (is (= "https://github.com/cgrand/moustache"
         (attribute (find-it dr-plain {:text "Moustache"}) "href")))
  (is (= "Moustache"
         (text (find-it dr-plain :a {:class #"exter"}))))
  (is (= "Moustache"
         (text (find-it dr-plain :a {:text #"Mous"}))))
  (is (= "Moustache"
         (text (find-it dr-plain :a {:class "external", :href #"github"}))))
  (is (= "Moustache"
         (text (find-it dr-plain :a {:class #"exter", :href #"github"}))))
  (is (= "Moustache"
         (text (find-it dr-plain [:div {:id "content"}, :a {:class "external"}]))))
  (is (= "Moustache"
         (text (find-it dr-plain [:div {:id "content"}, :a {:class #"exter"}]))))
  (is (= "Moustache"
         (text (find-it dr-plain [:div {:id "content"}, :a {:href #"github"}]))))
  (is (= "home"
         (text (find-it dr-plain [:* {:id "footer"}, :a {}]))))
  (is (= 8
         (count (find-them dr-plain :a))))
  (is (= 3
         (count (find-them dr-plain {:class #"-item"}))))
  (is (= 3
         (count (find-them dr-plain :a {:class #"-item"}))))
  (is (= 1
         (count (find-them dr-plain :a {:text #"hom"}))))
  (is (= 1
         (count (find-them dr-plain :a {:text #"(?i)HOM"}))))
  (is (= 2
         (count (find-them dr-plain :a {:class #"exter", :href #"github"}))))
  (is (= 3
         (count (find-them dr-plain [:* {:id "footer"}, :a {}]))))
  (is (= 2
         (count (find-them dr-plain [:div {:id "content"}, :a {:class #"exter"}]))))
  (-> dr-plain
      (find-it :a {:text "example form"})
      click)
  (is (= "first_name"
         (attribute (find-it dr-plain {:type "text"}) "id")))
  (is (= "first_name"
         (attribute (find-it dr-plain :input {:type "text"}) "id")))
  (is (= "first_name"
         (attribute (find-it dr-plain :input {:type "text", :name "first_name"}) "id")))
  (is (= "first_name"
         (attribute (find-it dr-plain :input {:type "text", :name #"first_"}) "id")))
  (is (= "last_name"
         (attribute (find-it dr-plain :input {:type "text", :name #"last_"}) "id")))
  (is (= "Smith"
         (attribute (find-it dr-plain :input {:type "text", :name #"last_"}) "value")))
  (is (= "Smith"
         (attribute (find-it dr-plain :input {:type "text", :name #"last_"}) "value")))
  (is (= "Smith"
         (attribute (find-it dr-plain [:div {:id "content"}, :input {:name #"last_"}]) "value")))
  (back dr-plain) ;; get back to home page
  (is (-> dr-plain
        (find-it :a)
        exists?))
  (is (not
       (-> dr-plain
           (find-it :area)
           exists?)))
  (is (nil?
       (-> dr-plain
           (find-it :area)
           exists?)))
  (is (-> dr-plain 
          (find-it :a {:text "Moustache"})
          visible?))
  (is (-> dr-plain 
          (find-it :a {:text "Moustache"})
          displayed?))
  (is (-> dr-plain
          (find-it :a {:text "Moustache"})
          present?))
  (is (not
       (-> dr-plain
           (find-it :a)
           visible?)))
  (is (not
       (-> dr-plain 
           (find-it :a)
           displayed?)))
  (is (not
       (-> dr-plain
           (find-it :a)
           present?)))
  (is (thrown? org.openqa.selenium.NoSuchElementException
               (find-it dr-plain :area))))

(deftest plain-test-form-elements
  (to dr-plain (str test-base-url "example-form"))
  ;; Clear element
  (-> dr-plain
      (find-it [:form {:id "example_form"}, :input {:name #"last_"}])
      clear)
  (is (= ""
         (value (find-it dr-plain [:form {:id "example_form"}, :input {:name #"last_"}]))))
  ;; Radio buttons
  (is (= true
         (selected? (find-it dr-plain :input {:type "radio", :value "male"}))))
  (-> dr-plain
      (find-it :input {:type "radio", :value "female"})
      select)
  (is (= true
         (selected? (find-it dr-plain :input {:type "radio", :value "female"}))))
  (-> dr-plain
      (find-it :radio {:value "male"})
      select)
  (is (= true
         (selected? (find-it dr-plain :input {:type "radio", :value "male"}))))
  ;; Checkboxes
  (is (= false
         (selected? (find-it dr-plain :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> dr-plain
      (find-it :input {:type "checkbox", :name #"(?i)clojure"})
      toggle)
  (is (= true
         (selected? (find-it dr-plain :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> dr-plain
      (find-it :checkbox {:name #"(?i)clojure"})
      click)
  (is (= false
         (selected? (find-it dr-plain :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> dr-plain
      (find-it :checkbox {:type "checkbox", :name #"(?i)clojure"})
      select)
  (is (= true
         (selected? (find-it dr-plain :input {:type "checkbox", :name #"(?i)clojure"}))))
  ;; Text fields
  (is (= "true"
         (attribute (find-it dr-plain :input {:type "text", :value "Testing!"})
                    "readonly")))
  (-> dr-plain
      (find-it :input {:id "first_name"})
      (input-text "foobar"))
  (is (= "foobar"
         (value (find-it dr-plain :input {:id "first_name"}))))
  (-> dr-plain
      (find-it :textfield {:id "first_name"})
      clear
      (input-text "clojurian"))
  (is (= "clojurian"
         (value (find-it dr-plain :textfield {:id "first_name"})))))

(deftest plain-test-form-helpers
  (to dr-plain (str test-base-url "example-form"))
  (quick-fill dr-plain
              [{"first_name" clear}
               {"first_name" "Richard"}
               {{:id "last_name"} clear}
               {{:id "last_name"} "Hickey"}
               {{:name "bio"} clear}
               {{:name "bio"} #(input-text % "Creator of Clojure")}
               {{:tag-name "input", :type "radio", :value "female"} click}
               {{:css "select#countries"} #(select-by-value % "france")}])
  (is (= "Richard"
         (value (find-it dr-plain :input {:id "first_name"}))))
  (is (= "Hickey"
         (value (find-it dr-plain :input {:id "last_name"}))))
  (is (= "Creator of Clojure"
         (value (find-it dr-plain :input {:name "bio"}))))
  (is (selected?
       (find-it dr-plain :input {:type "radio", :value "female"})))
  (is (selected?
       (find-it dr-plain :option {:value "france"}))))

(deftest plain-test-window-handling
  (is (= 1
         (count (window-handles dr-plain))))
  (is (= "Ministache"
         (:title (window-handle dr-plain))))
  (-> dr-plain
      (find-it :a {:text "is amazing!"})
      click)
  (is (= "Ministache"
         (:title (window-handle dr-plain))))
  (is (= 2
         (count (window-handles dr-plain))))
  (switch-to-window dr-plain (second (window-handles dr-plain)))
  (is (= (str test-base-url "clojure")
         (:url (window-handle dr-plain))))
  (switch-to-other-window dr-plain)
  (is (= test-base-url
         (:url (window-handle dr-plain))))
  (-> dr-plain
      (switch-to-window (find-it dr-plain :window {:url (str test-base-url "clojure")})))
  (close dr-plain)
  (is (= test-base-url
         (:url (window-handle dr-plain)))))

(deftest plain-wait-until-should-wait-for-condition
  (is (= "Ministache" (title dr-plain)))
  (-> dr-plain
    (execute-script "setTimeout(function () { window.document.title = \"asdf\"}, 3000)")
    (wait-until (fn [d] (= "asdf" (title d)))))
  (is (= "asdf" (title dr-plain))))

(deftest plain-wait-until-should-throw-on-timeout
  (is (thrown? TimeoutException
               (-> dr-plain
                 (execute-script "setTimeout(function () { window.document.title = \"test\"}, 6000)")
                 (wait-until (fn [d] (= "test" (title d))))))))

(deftest plain-wait-until-should-allow-timeout-argument
  (is (thrown? TimeoutException
               (-> dr-plain
                   (execute-script "setTimeout(function () { window.document.title = \"test\"}, 10000)")
                   (wait-until (fn [d] (= "test" (title d))) 1000)))))

(deftest plain-implicit-wait-should-cause-find-to-wait
  (-> dr-plain
      (implicit-wait 3000)
      (execute-script "setTimeout(function () { window.document.body.innerHTML = \"<div id='test'>hi!</div>\"}, 1000)"))
  (is (= "test"
         (attribute (find-element dr-plain (by-id "test")) :id))))

;; Not sure how we'll test that flash in fact flashes,
;; but at least this will catch changing API's
(deftest plain-test-flash-helper
  (-> dr-plain
      (find-it :a {:text "Moustache"})
      flash))



;; ## Raw Java WebDriver Tests ##
(deftest wdr-test-browser-basics
  (is (contains? (supers (class wdr)) org.openqa.selenium.WebDriver))
  (is (= test-base-url (current-url wdr)))
  (is (= "Ministache" (title wdr)))
  (is (boolean (re-find #"(?i)<!DOCTYPE html>" (page-source wdr)))))

(deftest wdr-test-back-forward
  (-> wdr
      (find-it :a {:text "example form"})
      click)
  (is (= (str test-base-url "example-form") (current-url wdr)))
  (back wdr)
  (is (= test-base-url (current-url wdr)))
  (forward wdr)
  (is (= (str test-base-url "example-form") (current-url wdr))))

(deftest wdr-test-to
  (to wdr (str test-base-url "example-form"))
  (is (= (str test-base-url "example-form") (current-url wdr)))
  (is (= "Ministache" (title wdr))))

(deftest wdr-test-bys
  (-> wdr
      (find-it :a {:text "example form"})
      click)
  (is (= "first_name"
         (attribute (find-element wdr (by-id "first_name")) :id)))
  (is (= "home"
         (text (find-element wdr (by-link-text "home")))))
  (is (= "example form"
         (text (find-element wdr (by-partial-link-text "example")))))
  (is (= "first_name"
         (attribute (find-element wdr (by-name "first_name")) :id)))
  (is (= "home"
         (text (find-element wdr (by-tag-name "a")))))
  (is (= "home"
         (text (find-element wdr (by-xpath "//a[text()='home']")))))
  (is (= "home"
         (text (find-element wdr (by-class-name "menu-item")))))
  (is (= "home"
         (text (find-element wdr (by-css-selector "#footer a.menu-item"))))))

(deftest wdr-test-find*
  (is (= "Moustache"
         (text (find-it wdr :a {:text "Moustache"}))))
  (is (= "Moustache"
         (text (find-it wdr {:class "external"}))))
  (is (= "https://github.com/cgrand/moustache"
         (attribute (find-it wdr {:text "Moustache"}) "href")))
  (is (= "Moustache"
         (text (find-it wdr :a {:class #"exter"}))))
  (is (= "Moustache"
         (text (find-it wdr :a {:text #"Mous"}))))
  (is (= "Moustache"
         (text (find-it wdr :a {:class "external", :href #"github"}))))
  (is (= "Moustache"
         (text (find-it wdr :a {:class #"exter", :href #"github"}))))
  (is (= "Moustache"
         (text (find-it wdr [:div {:id "content"}, :a {:class "external"}]))))
  (is (= "Moustache"
         (text (find-it wdr [:div {:id "content"}, :a {:class #"exter"}]))))
  (is (= "Moustache"
         (text (find-it wdr [:div {:id "content"}, :a {:href #"github"}]))))
  (is (= "home"
         (text (find-it wdr [:* {:id "footer"}, :a {}]))))
  (is (= 8
         (count (find-them wdr :a))))
  (is (= 3
         (count (find-them wdr {:class #"-item"}))))
  (is (= 3
         (count (find-them wdr :a {:class #"-item"}))))
  (is (= 1
         (count (find-them wdr :a {:text #"hom"}))))
  (is (= 1
         (count (find-them wdr :a {:text #"(?i)HOM"}))))
  (is (= 2
         (count (find-them wdr :a {:class #"exter", :href #"github"}))))
  (is (= 3
         (count (find-them wdr [:* {:id "footer"}, :a {}]))))
  (is (= 2
         (count (find-them wdr [:div {:id "content"}, :a {:class #"exter"}]))))
  (-> wdr
      (find-it :a {:text "example form"})
      click)
  (is (= "first_name"
         (attribute (find-it wdr {:type "text"}) "id")))
  (is (= "first_name"
         (attribute (find-it wdr :input {:type "text"}) "id")))
  (is (= "first_name"
         (attribute (find-it wdr :input {:type "text", :name "first_name"}) "id")))
  (is (= "first_name"
         (attribute (find-it wdr :input {:type "text", :name #"first_"}) "id")))
  (is (= "last_name"
         (attribute (find-it wdr :input {:type "text", :name #"last_"}) "id")))
  (is (= "Smith"
         (attribute (find-it wdr :input {:type "text", :name #"last_"}) "value")))
  (is (= "Smith"
         (attribute (find-it wdr :input {:type "text", :name #"last_"}) "value")))
  (is (= "Smith"
         (attribute (find-it wdr [:div {:id "content"}, :input {:name #"last_"}]) "value")))
  (back wdr) ;; get back to home page
  (is (-> wdr
        (find-it :a)
        exists?))
  (is (not
       (-> wdr
           (find-it :area)
           exists?)))
  (is (nil?
       (-> wdr
           (find-it :area)
           exists?)))
  (is (-> wdr 
          (find-it :a {:text "Moustache"})
          visible?))
  (is (-> wdr 
          (find-it :a {:text "Moustache"})
          displayed?))
  (is (-> wdr
          (find-it :a {:text "Moustache"})
          present?))
  (is (not
       (-> wdr
           (find-it :a)
           visible?)))
  (is (not
       (-> wdr 
           (find-it :a)
           displayed?)))
  (is (not
       (-> wdr
           (find-it :a)
           present?)))
  (is (thrown? org.openqa.selenium.NoSuchElementException
               (find-it wdr :area))))

(deftest wdr-test-form-elements
  (to wdr (str test-base-url "example-form"))
  ;; Clear element
  (-> wdr
      (find-it [:form {:id "example_form"}, :input {:name #"last_"}])
      clear)
  (is (= ""
         (value (find-it wdr [:form {:id "example_form"}, :input {:name #"last_"}]))))
  ;; Radio buttons
  (is (= true
         (selected? (find-it wdr :input {:type "radio", :value "male"}))))
  (-> wdr
      (find-it :input {:type "radio", :value "female"})
      select)
  (is (= true
         (selected? (find-it wdr :input {:type "radio", :value "female"}))))
  (-> wdr
      (find-it :radio {:value "male"})
      select)
  (is (= true
         (selected? (find-it wdr :input {:type "radio", :value "male"}))))
  ;; Checkboxes
  (is (= false
         (selected? (find-it wdr :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> wdr
      (find-it :input {:type "checkbox", :name #"(?i)clojure"})
      toggle)
  (is (= true
         (selected? (find-it wdr :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> wdr
      (find-it :checkbox {:name #"(?i)clojure"})
      click)
  (is (= false
         (selected? (find-it wdr :input {:type "checkbox", :name #"(?i)clojure"}))))
  (-> wdr
      (find-it :checkbox {:type "checkbox", :name #"(?i)clojure"})
      select)
  (is (= true
         (selected? (find-it wdr :input {:type "checkbox", :name #"(?i)clojure"}))))
  ;; Text fields
  (is (= "true"
         (attribute (find-it wdr :input {:type "text", :value "Testing!"})
                    "readonly")))
  (-> wdr
      (find-it :input {:id "first_name"})
      (input-text "foobar"))
  (is (= "foobar"
         (value (find-it wdr :input {:id "first_name"}))))
  (-> wdr
      (find-it :textfield {:id "first_name"})
      clear
      (input-text "clojurian"))
  (is (= "clojurian"
         (value (find-it wdr :textfield {:id "first_name"})))))

(deftest wdr-test-form-helpers
  (to wdr (str test-base-url "example-form"))
  (quick-fill wdr
              [{"first_name" clear}
               {"first_name" "Richard"}
               {{:id "last_name"} clear}
               {{:id "last_name"} "Hickey"}
               {{:name "bio"} clear}
               {{:name "bio"} #(input-text % "Creator of Clojure")}
               {{:tag-name "input", :type "radio", :value "female"} click}
               {{:css "select#countries"} #(select-by-value % "france")}])
  (is (= "Richard"
         (value (find-it wdr :input {:id "first_name"}))))
  (is (= "Hickey"
         (value (find-it wdr :input {:id "last_name"}))))
  (is (= "Creator of Clojure"
         (value (find-it wdr :input {:name "bio"}))))
  (is (selected?
       (find-it wdr :input {:type "radio", :value "female"})))
  (is (selected?
       (find-it wdr :option {:value "france"}))))

(deftest wdr-test-window-handling
  (is (= 1
         (count (window-handles wdr))))
  (is (= "Ministache"
         (:title (window-handle wdr))))
  (-> wdr
      (find-it :a {:text "is amazing!"})
      click)
  (is (= "Ministache"
         (:title (window-handle wdr))))
  (is (= 2
         (count (window-handles wdr))))
  (switch-to-window wdr (second (window-handles wdr)))
  (is (= (str test-base-url "clojure")
         (:url (window-handle wdr))))
  (switch-to-other-window wdr)
  (is (= test-base-url
         (:url (window-handle wdr))))
  (-> wdr
      (switch-to-window (find-it wdr :window {:url (str test-base-url "clojure")})))
  (close wdr)
  (is (= test-base-url
         (:url (window-handle wdr)))))

(deftest wdr-wait-until-should-wait-for-condition
  (is (= "Ministache" (title wdr)))
  (-> wdr
    (execute-script* "setTimeout(function () { window.document.title = \"asdf\"}, 3000)")
    (wait-until (fn [d] (= "asdf" (title d)))))
  (is (= "asdf" (title wdr))))

(deftest wdr-wait-until-should-throw-on-timeout
  (is (thrown? TimeoutException
               (-> wdr
                 (execute-script* "setTimeout(function () { window.document.title = \"test\"}, 6000)")
                 (wait-until (fn [d] (= "test" (title d))))))))

(deftest wdr-wait-until-should-allow-timeout-argument
  (is (thrown? TimeoutException
               (-> wdr
                   (execute-script* "setTimeout(function () { window.document.title = \"test\"}, 10000)")
                   (wait-until (fn [d] (= "test" (title d))) 1000)))))

(deftest wdr-implicit-wait-should-cause-find-to-wait
  (-> wdr
      (implicit-wait 3000)
      (execute-script* "setTimeout(function () { window.document.body.innerHTML = \"<div id='test'>hi!</div>\"}, 1000)"))
  (is (= "test"
         (attribute (find-element wdr (by-id "test")) :id))))

;; Not sure how we'll test that flash in fact flashes,
;; but at least this will catch changing API's
(deftest wdr-test-flash-helper
  (-> wdr
      (find-it :a {:text "Moustache"})
      flash))
