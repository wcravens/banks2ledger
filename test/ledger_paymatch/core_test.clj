(ns ledger-paymatch.core-test
  (:require [clojure.test :refer :all]
            [ledger-paymatch.core :refer :all]))

;; Compare a and b for "equal enough" (used for testing float results)
(defn f= [a b]
  (cond (or (list? a) (vector? a))
        (do (println "comp: a: " a "b: " b)
            (and (f= (first a)) (f= (first b))
                 (f= (rest a)) (f= (rest b))))
        (and (float? a) (float? b))
        (do (println "float: a: " a "b: " b "eq:" (< (Math/abs (- a b)) 1E-6))
            (< (Math/abs (- a b)) 1E-6))
        :else
        (do (println "simp: a: " a "b: " b)
            (= a b))))

(deftest test-toktab-inc
  (testing "toktab-inc"
    (is (= (toktab-inc {} ["Account" "tok1"])
           {"Account" {"tok1" 1}}))
    (is (= (toktab-inc {"Account" {"tok1" 1}} ["Account" "tok1"])
           {"Account" {"tok1" 2}}))
    (is (= (toktab-inc {"Account" {"tok1" 2}} ["Account" "tok2"])
           {"Account" {"tok1" 2, "tok2" 1}}))))

(deftest test-toktab-update
  (testing "toktab-update"
    (is (= (toktab-update {} {:accs ["Acc1" "Acc2"] :toks ["tok1" "tok2"]})
           {"Acc1" {"tok1" 1, "tok2" 1}, "Acc2" {"tok1" 1, "tok2" 1}}))
    (is (= (toktab-update {"Acc1" {"tok1" 1, "tok2" 1},
                           "Acc2" {"tok1" 1, "tok2" 1}}
                          {:accs ["Acc1" "Acc3"] :toks ["tok1" "tok3"]})
           {"Acc1" {"tok1" 2, "tok2" 1, "tok3" 1},
            "Acc2" {"tok1" 1, "tok2" 1},
            "Acc3" {"tok1" 1, "tok3" 1}}))))

(deftest test-clip-string
  (testing "clip-string"
    (is (= (clip-string "|" "abcdefg") "abcdefg"))
    (is (= (clip-string "|" "abcdef|ghij") "abcdef"))
    (is (= (clip-string "  " " abcd  efg") " abcd"))))

(deftest test-tokenize
  (testing "tokenize"
    (is (= (tokenize "Forgalmi jutalék 01N00/702595/ számláról")
           '("FORGALMI" "JUTALÉK" "01N00" "702595" "SZÁMLÁRÓL")))
    (is (= (tokenize "Vásárlás LIBRI ARKAD /N 20060923")
           '("VÁSÁRLÁS" "LIBRI" "ARKAD" "N" "YYYYMMDD")))
    (is (= (tokenize "APOTEK HJART/16-03-21")
           '("APOTEK" "HJART" "YY-MM-DD")))
    (is (= (tokenize "COOP KONSUM /16-03-17")
           '("COOP" "KONSUM" "YY-MM-DD")))))

(deftest test-p_occur
  (testing "p_occur"
    (is (= (p_occur {"Acc1" {"tok1" 2, "tok2" 1, "tok3" 1}} "tok1" "Acc1") 0.5))
    (is (= (p_occur {"Acc1" {"tok1" 2, "tok2" 1, "tok3" 1}} "tok2" "Acc1") 0.25))
    (is (= (p_occur {"Acc1" {"tok1" 2, "tok2" 1, "tok3" 1}} "tok4" "Acc1") 0.0))
    (is (= (p_occur {"Acc1" {"tok1" 2, "tok2" 1, "tok3" 1}} "tok" "Acc2") 0.0))))

(deftest test-p_belong
  (testing "p_belong"
    (is (= (p_belong {"Acc1" {"tok1" 2, "tok2" 1},
                      "Acc2" {"tok1" 2, "tok2" 1}} "tok1" "Acc1")
           (float 1/2)))
    (is (f= (p_belong {"Acc1" {"tok1" 1, "tok2" 2},
                       "Acc2" {"tok1" 1, "tok2" 5}} "tok1" "Acc1")
            (float (/ 1/3 (+ 1/3 1/6)))))
    (is (f= (p_belong {"Acc1" {"tok1" 1, "tok2" 2},
                       "Acc2" {"tok1" 1, "tok2" 5},
                       "Acc3" {"tok1" 1, "tok2" 8}} "tok1" "Acc1")
            (float (/ 1/3 (+ 1/3 1/6 1/9)))))))

(deftest test-best-accounts
  (testing "best-accounts"
    (is (f= (best-accounts {"Acc1" {"tok1" 1, "tok2" 2},
                            "Acc2" {"tok1" 1, "tok2" 8},
                            "Acc3" {"tok2" 8},
                            "Acc4" {"tok1" 1, "tok2" 5}} "tok1")
            '([0.5454545454545454 "Acc1"]
              [0.2727272727272727 "Acc4"]
              [0.1818181818181818 "Acc2"])))))

;;(deftest test-
;;  (testing ""
;;    (is (=

(deftest test-parse-ledger-entry
  (testing "parse-ledger-entry"
    (is (= (parse-ledger-entry
            (str "2016/03/22 ICA NARA KAR/16-03-21\n"
                 "    Expenses:Groceries:ICA                SEK 314.32\n"
                 "    Assets:Bank account\n\n"))
           {:date "2016/03/22",
            :toks '("ICA" "NARA" "KAR" "YY-MM-DD"),
            :accs '("Expenses:Groceries:ICA" "Assets:Bank account")}))
    (is (= (parse-ledger-entry
            (str "2016/02/16 Lindra Second Hand, Kärrtorp | Baby stuff\n"
                 "    Expenses:Clothing:Baby                 SEK 60.00\n"
                 "    Assets:Bank account\n"))
           {:date "2016/02/16",
            :toks '("LINDRA" "SECOND" "HAND" "KÄRRTORP"),
            :accs '("Expenses:Clothing:Baby" "Assets:Bank account")}))))

(deftest test-get-arg
  (testing "get-arg"
    (is (= (get-arg cl-args-spec :amount-col) 2))
    (is (= (get-arg cl-args-spec :descr-col) 3))))

(deftest test-parse-args
  (testing "parse-args"
    (is (= (:value (:csv-file (parse-args cl-args-spec '("-f" "abcde.csv"))))
           "abcde.csv"))
    (is (= (:value (:amount-col (parse-args cl-args-spec '("-m" "4"))))
           "4"))))

(deftest test-convert-date
  (testing "convert-date"
    (is (= (convert-date {:date-format {:value "yyyy-MM-dd"}} "2016-04-24")
           "2016/04/24"))
    (is (= (convert-date {:date-format {:value "MM/dd/yy"}} "04/24/16")
           "2016/04/24"))
    (is (= (convert-date {:date-format {:value "yyyy/MM/dd"}} "2016/04/24")
           "2016/04/24"))))

(deftest test-convert-amount
  (testing "convert-amount"
    (is (= (convert-amount "-123,45 kr") "-123.45"))
    (is (= (convert-amount "garbage +123.5 kr") "123.50"))
    (is (= (convert-amount "12345") "12,345.00"))
    (is (= (convert-amount "-1234567") "-1,234,567.00"))))

(deftest test-unquote-string
  (testing "unquote-string"
    (is (= (unquote-string "abcdef") "abcdef"))
    (is (= (unquote-string "\"abcdef\"") "abcdef"))
    (is (= (unquote-string "\"abcdef") "\"abcdef"))
    (is (= (unquote-string "abcdef\"") "abcdef\""))
    (is (= (unquote-string "'abcdef'") "abcdef"))
    (is (= (unquote-string "'abcdef") "'abcdef"))
    (is (= (unquote-string "abcdef'") "abcdef'"))))

(deftest test-all-indices
  (testing "all-indices"
    (is (= (all-indices "abcdef" ",")
           []))
    (is (= (all-indices ",abc,def," ",")
           [0 4 8]))
    (is (= (all-indices "abc,de,\"f,g,x\",hi,\"al,ma\"" ",")
           [3 6 9 11 14 17 21]))))

(deftest test-split-csv-line
  (testing "split-csv-line"
    (is (= (split-csv-line "abc,def,ghi" ",")
           ["abc" "def" "ghi"]))
    (is (= (split-csv-line "abc,de,\"f,g,x\",hi,\"al,ma\"" ",")
           ["abc" "de" "\"f,g,x\"" "hi" "\"al,ma\""]))))
