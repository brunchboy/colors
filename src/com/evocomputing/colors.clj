;; by Joel Boehland http://github.com/jolby/colors
;; February 4, 2010

;; Copyright (c) Joel Boehland, 2010. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

(ns
    #^{:doc
       "Color manipulation routines. This is mostly a port of the
color.rb module in the ruby SASS project to Clojure:
http://github.com/nex3/haml/blob/master/lib/sass/script/color.rb

Further references:
HSL and HSV: http://en.wikipedia.org/wiki/Luminance-Hue-Saturation
RGB color space: http://en.wikipedia.org/wiki/RGB_color_space
http://en.wikipedia.org/wiki/Hue#Computing_hue_from_RGB
http://www.w3.org/TR/css3-color/#hsl-color
"
       :author "Joel Boehland"}

  com.evocomputing.colors
  (import (java.awt Color))
  (:use (clojure.contrib core except math str-utils seq-utils)))

(declare html4-colors-name-to-rgbnum html4-colors-name-to-rgb
         html4-colors-rgbnum-to-name html4-colors-rgb-to-name
         rgb-int-to-components rgba-int-to-components
         rgb-int-from-components rgba-int-from-components
         rgb-to-hsl hsl-to-rgb)

(defstruct
    #^{:doc
       "Structure representing a color. Default representation
    is an array of integers mapping to the respective RGB(A)
    values. This structure also supports holding an array of float
    values mapping to the respective HSL values as well"}
  color
  ;;4 integer array representation of the rgba values. Rgba values
  ;;must be between 0 and 255 inclusive
  :rgba
  ;;3 float array holding the HSL values for this color. The
  ;;saturation and lightness must be between 0.0 and 100.0. Hue must
  ;;be between 0.0 and 360.0
  :hsl)

(def allowable-rgb-keys
     #{:r :red :g :green :b :blue})

(def allowable-hsl-keys
     #{:h :hue :s :saturation :l :lightness})

(defn hexstring-to-rgba-int
  [hexstr]
  (if-let [matches (re-find #"(^#|^0[Xx])([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$" hexstr)]
    (Long/decode
     (condp =  (count (matches 2))
       3 (apply str "0xff" (map #(str % %) (matches 2)))
       6 (apply str "0xff" (matches 2))
       8 (apply str "0x" (matches 2))))))

;;Resolution/normalize code taken from Ruby color:
;;http://rubyforge.org/projects/color
(def #^{:doc "The maximum resolution for colour math; if any value is less than or
   equal to this value, it is treated as zero."}
     color-epsilon 0.00001)

(def #^{:doc "The tolerance for comparing the components of two colours. In general,
  colours are considered equal if all of their components are within this
  tolerance value of each other."}
 color-tolerance 0.0001)

(defn within-tolerance?
  [fval1 fval2]
  (<= (abs (- fval1 fval2)) color-tolerance))

(defn near-zero?
  "Returns true if the fvalue is less than color-epsilon."
  [fval]
  (<= (abs fval) color-epsilon))

(defn near-zero-or-less?
  "Returns true if the fvalue is within color-epsilon of zero or less than zero."
  [fval]
  (or (< fval 0.0) (near-zero? fval)))

(defn near-one?
  "Returns true if fvalue is within color-epsilon of one"
  [fval]
  (near-zero? (- 1.0 fval)))

(defn rgb-int?
  "If the passed in value is an integer in the range 0 - 255
  inclusive, return true, otherwise return false"
  [rgb-int]
  (and (integer? rgb-int) (and (>= rgb-int 0) (<= rgb-int 255))))

(defn unit-float?
  "Return true if passed in float is in the range 0.0 - 1.0. False otherwise"
  [fval]
  (and (>= fval 0.0) (<= fval 1.0)))

(defn percent-float?
  "Return true if passed in float is in the range 0.0 - 100.0. False otherwise"
  [fval]
  (and (>= fval 0.0) (<= fval 100.0)))

(defn circle-float?
  "Return true if passed in float is in the range 0.0 - 360.0. False otherwise"
  [fval]
  (and (>= fval 0.0) (<= fval 360.0)))

(defn clamp-rgb-int
  "Clamp the integer value to be within the range 0 - 255"
  [rgb-int]
  (max (min rgb-int 255) 0))

(defn clamp-unit-float
  [ufloat]
  (max (min ufloat 1.0) 0.0))

(defn clamp-percent-float
  [pfloat]
  (max (min pfloat 100.0) 0.0))

(defn clamp-hue
  "Clamp the hue value so that is lies on the range 0.0 - 360.0"
  [hue]
  (mod hue 360.0))

(defn unit-float-to-rgba-int
  "Check that the passed in float is in the range 0.0 - 1.0, then
convert it to the appropriate integer in the range 0 - 255"
  [fval]
  (throw-if-not (unit-float? fval)
                "fval must be a float between 0.0 and 0.1: %s" fval)
  (int (+ 0.5 (* fval 255))))

(defn rgb-int-to-unit-float
 "Convert the integer in range 0 - 255 to float in range 0.0 - 1.0"
 [rgb-int]
 (throw-if-not (rgb-int? rgb-int) "Must be integer in range 0 - 255")
 (/ rgb-int 255.0))

(defn maybe-convert-alpha
  "If alpha is a float value, try to convert to integer in range 0 -
255, otherwise return as-is"
  [alpha]
  (if (rgb-int? alpha) alpha
      (do
        (throw-if-not (unit-float? alpha)
                      "alpha must be an integer in range 0 - 255 or unit float: %s" alpha)
        (unit-float-to-rgba-int alpha))))

(defn check-rgb
  "Check that every element in the passed in rgba sequence is an
integer in the range 0 - 255"
  ([rgb]
     (throw-if-not (and (= (count rgb) 3) (every? #'rgb-int? rgb))
                   "Must contain 3 integers in range 0 - 255: %s" rgb)
     rgb)
  ([r g b]
     (throw-if-not (every? #'rgb-int? [r g b])
     "Must contain 3 integers in range 0 - 255: %s" [r g b])))

(defn check-rgba
  "Check that every element in the passed in rgba sequence is an
integer in the range 0 - 255"
  ([rgba]
     (throw-if-not (and (= (count rgba) 4) (every? #'rgb-int? rgba))
                   "Must contain 4 integers in range 0 - 255: %s" rgba)
     rgba)
  ([r g b a]
     (throw-if-not (every? #'rgb-int? [r g b a])
     "Must contain 4 integers in range 0 - 255: %s" [r g b a])))

(defn check-hsl
  "Check that every element is of the format:
- 1st, H (Hue): Float value in the range of: 0.0 - 360.0
- 2nd, S (Saturation): Float value in the range: 0.0 - 100.0
- 3rd, L (Lightness or Luminance): Float value in the range 0.0 - 100.0
"
  ([hsl]
     (throw-if-not (and (= (count hsl) 3) (not (some nil? hsl)))
                   "Must contain 3 floats representing HSL: %s" hsl)
     (check-hsl (hsl 0) (hsl 1) (hsl 2))
     [(clamp-hue (hsl 0)) (hsl 1) (hsl 2)])
  ([h s l] (throw-if-not (and (circle-float? (clamp-hue h))
                              (percent-float? s) (percent-float? l))
                         "Elements must be of the form:
H (Hue): Float value in the range of: 0.0 - 360.0
S (Saturation): Float value in the range: 0.0 - 100.0
L (Lightness or Luminance): Float value in the range 0.0 - 100.0
%s %s %s" h s l)))


(defn create-color-dispatch
  ""
  ([args]
  (cond
   (or (symbol? args) (string? args) (keyword? args)) ::symbolic-color
   (integer? args) ::rgb-int
   (and (map? args) (some allowable-rgb-keys (keys args))) ::rgb-map
   (and (map? args) (some allowable-hsl-keys (keys args))) ::hsl-map
   (and (or (seq? args) (seqable? args)) (#{3 4} (count args))) ::rgb-array
   (= (class args) Color) Color
   true (throw (IllegalArgumentException.
                (format "Don't know how to process args: %s" args)))))
  ([arg & others]
     (let [args (conj others arg)]
       (cond
        (and (keyword? arg) (some allowable-rgb-keys args)) ::rgb-map
        (and (keyword? arg) (some allowable-hsl-keys args)) ::hsl-map
        (and (or (seq? args) (seqable? args)) (#{3 4} (count args))) ::rgb-array
        true (throw (IllegalArgumentException.
                     (format "Don't know how to process args: %s" arg)))))))

(defmacro create-color-with-meta
  "Create color with type meta"
  [& body]
  `(with-meta
     ~@body
      {:type ::color}))

(defmulti create-color
  "Create a color struct using the passed in args.

This will create a color struct that has RGBA integer values in the range:
- R (Red): Integer in range 0 - 255
- G (Green): Integer in range 0 - 255
- B (Blue): Integer in range 0 - 255
- A (Alpha): Integer in range 0 - 255, with default as 255 (100% opacity)

And HSL values with the range:
- H (Hue): Float value in the range of: 0.0 - 360.0
- S (Saturation): Float value in the range: 0.0 - 100.0
- L (Lightness or Luminance): Float value in the range 0.0 - 100.0

This multimethod is very liberal in what it will accept to create a
color. Following is a list of acceptable formats:

Single Arg
- Symbolic: Either a string or keyword or symbol that
matches an entry in the symbolic color pallette. Currently, this is
the html4 colors map, but there are plans to allow the symbolic color
map to be set to any custom pallette.
  examples:
  (create-color \"blue\")
  (create-color :blue)

- Hexstring: A hex string representation of an RGB(A) color
  examples:
  (create-color \"0xFFCCAA\")
  (create-color \"#FFCCAA\")
  (create-color \"Ox80FFFF00\") ;; alpha = 128

- Integer: An integer representation of an RGB(A) color
  examples:
  (create-color 0xFFCCAA) ;; integer in hexidecimal format
  (create-color 16764074) ;; same integer in decimal format

- Sequence or array of RGB(A) integers
  :examples
  (create-color [255 0 0])
  (create-color [255 0 0 128]) ;;alpha = 128

- Map of either RGB (A) kw/values or HSL(A) kw/values
  Allowable RGB keys: :r :red :g :green :b :blue
  Allowable HSL keys: :h :hue :s :saturation :l :lightness

  examples:
  (create-color {:r 255 :g 0 :blue 0})
  (create-color {:r 255 :g 0 :blue 0 :a 128})
  (create-color {:h 120.0 :s 100.0 :l 50.0})
  (create-color {:h 120.0 :s 100.0 :l 50.0 :a 128})

Multiple Arg
- Sequence or array of RGB(A) integers
  :examples
  (create-color 255 0 0)
  (create-color 255 0 0 128) ;;alpha = 128

- Assoc list of either RGB (A) kw/values or HSL(A) kw/values
  Allowable RGB keys: :r :red :g :green :b :blue
  Allowable HSL keys: :h :hue :s :saturation :l :lightness

  examples:
  (create-color :r 255 :g 0 :blue 0)
  (create-color :r 255 :g 0 :blue 0 :a 128)
  (create-color :h 120.0 :s 100.0 :l 50.0)
  (create-color :h 120.0 :s 100.0 :l 50.0 :a 128)
"

  create-color-dispatch)

(defmethod create-color ::symbolic-color [colorsym]
  (letfn [(stringify [colorsym]
             (if (or (symbol? colorsym) (keyword? colorsym))
               (.toLowerCase (name colorsym))
               colorsym))]
    (let [colorsym (stringify colorsym)]
      (if-let [rgb-int (html4-colors-name-to-rgbnum colorsym)]
        (create-color (rgb-int-to-components rgb-int))
        (create-color
         (rgba-int-to-components (hexstring-to-rgba-int colorsym)))))))

(defmethod create-color ::rgb-int [rgb-int]
  (create-color (rgba-int-to-components rgb-int)))

(defmethod create-color ::rgb-array [rgb-array & others]
  (let [rgb-array (if others (vec (conj others rgb-array)) rgb-array)
        ;;if alpha wasn't provided, use default of 255
        alpha (if (or (= 3 (count rgb-array)) (nil? (rgb-array 3))) 255
                  (maybe-convert-alpha (rgb-array 3)))
        rgba (conj (into [] (take 3 rgb-array)) alpha) ]
    (check-rgba rgba)
    (create-color-with-meta
      (struct color rgba
              (rgb-to-hsl (rgba 0) (rgba 1) (rgba 2))))))

(defmethod create-color ::rgb-map [rgb-map & others]
  (let [rgb-map (if others (apply assoc {} (vec (conj others rgb-map))) rgb-map)
        ks (keys rgb-map)
        rgb (into [] (map #(rgb-map %)
                           (map #(some % ks)
                                '(#{:r :red} #{:g :green} #{:b :blue}))))
        alpha (or (:a rgb-map) (:alpha rgb-map))
        rgba (check-rgba (if alpha (conj rgb alpha) (conj rgb 255)))]
    (create-color rgba)))

(defmethod create-color ::hsl-map [hsl-map & others]
  (let [hsl-map (if others (apply assoc {} (vec (conj others hsl-map))) hsl-map)
        ks (keys hsl-map)
        hsl (check-hsl (into [] (map #(hsl-map %)
                                     (map #(some % ks)
                                          '(#{:h :hue} #{:s :saturation} #{:l :lightness})))))
        rgb (hsl-to-rgb (hsl 0) (hsl 1) (hsl 2))
        alpha (maybe-convert-alpha (or (:a hsl-map) (:alpha hsl-map) 255))
        rgba (check-rgba (if alpha (conj rgb alpha) (conj rgb 255)))]
    (create-color-with-meta (struct color rgba hsl))))

(defmethod create-color Color [color]
  (create-color [(.getRed color) (.getGreen color)
                 (.getBlue color) (.getAlpha color)]))

(defn red "Return the red (int) component of this color" [color] ((:rgba color) 0))
(defn green "Return the green (int) component of this color" [color] ((:rgba color) 1))
(defn blue "Return the blue (int) component of this color" [color] ((:rgba color) 2))
(defn hue "Return the hue (float) component of this color" [color] ((:hsl color) 0))
(defn saturation "Return the saturation (float) component of this color" [color] ((:hsl color) 1))
(defn lightness "Return the lightness (float) component of this color" [color] ((:hsl color) 2))
(defn alpha "Return the alpha (int) component of this color" [color] ((:rgba color) 3))

(defn rgb-int
  "Return a integer (RGB) representation of this color"
  [color]
  (rgb-int-from-components (red color) (green color) (blue color)))

(defn rgba-int
  "Return a integer (RGBA) representation of this color"
  [color]
  (rgba-int-from-components (red color) (green color) (blue color) (alpha color)))

(defn rgba-hexstr
  "Return the hexcode string representation of this color"
  [color]
  (format "#%08X" (rgba-int color)))

(defn rgb-hexstr
  "Return the hexcode string representation of this color"
  [color]
  (format "#%06X" (rgb-int color)))

(defn color-name
  "If there is an entry for this color value in the symbolic color
names map, return that. Otherwise, return the hexcode string of this
color's rgba integer value"
  [color]
  (if-let [color-name (html4-colors-rgbnum-to-name (rgb-int color))]
    color-name
    (format "%#08x" (rgba-int color))))

(defmethod print-method ::color [color writer]
  (print-method (format "#<color: %s R: %d, G: %d, B: %d, H: %.2f, S: %.2f, L: %.2f, A: %d>"
                        (color-name color) (red color) (green color) (blue color)
                        (hue color) (saturation color) (lightness color) (alpha color))
                writer))

(defn color=
  "Return true if rgba components are equal, and hsl float components
are within tolerance"
  [color1 color2]
  (and (= (:rgba color1) (:rgba color2))
       (and (<= (abs (- (hue color1) (hue color2))) color-tolerance)
            (<= (abs (- (saturation color1) (saturation color2))) color-tolerance)
            (<= (abs (- (lightness color1) (lightness color2))) color-tolerance))))

(defn rgb-int-from-components
  "Convert a vector of the 3 rgb integer values into a color given in
  numeric rgb format"
  [r g b]
  (bit-or (bit-shift-left (bit-and r 0xFF) 16)
          (bit-or (bit-shift-left (bit-and g 0xFF) 8)
                 (bit-shift-left (bit-and b 0xFF) 0))))

(defn rgba-int-from-components
  "Convert a vector of the 4 rgba integer values into a color given in
  numeric rgb format "
  [r g b a]
  (bit-or (bit-shift-left (bit-and a 0xFF) 24)
          (bit-or (bit-shift-left (bit-and r 0xFF) 16)
                  (bit-or (bit-shift-left (bit-and g 0xFF) 8)
                          (bit-shift-left (bit-and b 0xFF) 0)))))

(defn rgb-int-to-components
  "Convert a color given in numeric rgb format into a vector of the 3
  rgb integer values"
  [rgb-int]
  (into []
        (reverse (for [n (range 0 3)]
                   (bit-and (bit-shift-right rgb-int (bit-shift-left n 3)) 0xff)))))

(defn rgba-int-to-components
  "Convert a color given in numeric rgb format into a vector of the 4
  rgba integer values"
  [rgba-int]
  (conj (rgb-int-to-components rgba-int)
        (bit-shift-right rgba-int 24)))

;;Color ops
(defmacro def-color-bin-op
  "Macro for creating binary operations between two colors.

Arguments
name - the name of the operation.

bin-op - the function that takes two values, producing one from
those. (eg: + - * /). This op will be applied pairwise to the
repective color's rgba components to create a new color with the
resultant rgba components.

Result
color - a new color that is the result of the binary operation."
  [name bin-op]
  `(defn ~name
     [color1# color2#]
     (create-color (vec (map clamp-rgb-int (map ~bin-op (:rgba color1#) (:rgba color2#)))))))

(def-color-bin-op color-add +)
(def-color-bin-op color-sub -)
(def-color-bin-op color-mult *)
(def-color-bin-op color-div /)

(defn mix [color1 color2 weight]
  (let [p (/ weight 100.0)
        w (- (* p 2) 1)
        a (- (alpha color1) (alpha color2))
        w1 (/ (+ 1
                 (if (= (* w a) -1) w
                     (/ (+ w a) (+ 1 (* w a)))))
              2.0)
        w2 (- 1 w1)
        rgb (vec (map #(clamp-rgb-int (int (+ (* %1 w1) (* %2 w2))))
                                      (take 3 (:rgba color1)) (take 3 (:rgba color2))))
        adj-alpha (int (+ (* (alpha color1) p) (* (alpha color2) (- 1 p)))) ]
    ;;(println (format "p: %s, w: %s, a: %s, w1: %s, w2: %s, rgb: %s, adj-alpha: %s, rgba: %s"
    ;;                 p w a w1 w2 rgb adj-alpha (conj rgb adj-alpha)))
    (create-color (conj rgb adj-alpha))))

(defn adjust-hue [color degrees]
  (create-color :h (clamp-hue (+ (hue color) degrees))
                :s (saturation color)
                :l (lightness color) :a (alpha color)))

(defn saturate [percent]
  (create-color :h (hue color)
                :s (clamp-percent-float (+ (saturation color) percent))
                :l (lightness color) :a (alpha color)))

(defn desaturate [percent]
  (create-color :h (hue color)
                :s (clamp-percent-float (- (saturation color) percent))
                :l (lightness color) :a (alpha color)))

(defn lighten [color percent]
  (create-color :h (hue color)
                :s (saturation color)
                :l (clamp-percent-float (+ (lightness color) percent))
                :a (alpha color)))

(defn darken  [color percent]
  (create-color :h (hue color)
                :s (saturation color)
                :l (clamp-percent-float (- (lightness color) percent))
                :a (alpha color)))

(defn adjust-alpha [color unit-float-adj]
  (create-color :h (hue color)
                :s (saturation color)
                :l (lightness color)
                :a (clamp-unit-float (+ (rgb-int-to-unit-float (alpha color))
                                        unit-float-adj))))

(defn grayscale [color]
  (desaturate color 100.0))

(defn opposite [color]
  (adjust-hue color 180))


(defn inclusive-seq [n start end]
  "Return n evenly spaced points along the range start - end (inclusive)"
  (when (< n 1) (throw-arg "n must be 1 or greater"))
  (if (= n 1) start
      (loop [acc [] step (/ (- end start) (- n 1)) num start idx 0]
            (if (= idx n) acc
                (recur (conj acc num) step (+ step num) (inc idx))))))

(defn rainbow-hsl
  "Computes a rainbow of colors (qualitative palette) defined by
different hues given a single value of each saturation and lightness.

Arguments:
numcolors: Number of colors to be produced in this pallette.

Optional Arguments:
:s (keyword) saturation. 0.0 - 100.0 (default 50.0)
:l (keyword) lightness. 0.0 - 100.0. (default 70.0)
:start (keyword) hue where rainbow starts. 0 - 360 (default 0)
:end (keyword) hue where rainbow ends. 0 - 360 (default: (* 360 (/ (- numcolors 1) numcolors)))

"
  [numcolors & opts]
  (let [opts (merge {:s 50.0 :l 70.0 :start 0 :end (* 360 (/ (- numcolors 1) numcolors))}
                    (when opts (apply assoc {} opts)))
        ;;hvals (range (:start opts) (inc (:end opts)) (/ (:end opts) (dec numcolors)))
        hvals (inclusive-seq numcolors (:start opts) (:end opts))]
    (printf (format "hvals: %s" (vec hvals)))
    (map #(create-color :h (float %) :s (:s opts) :l (:l opts))
         hvals)))

(defn diverge-hsl
  "Compute a set of colors diverging
from a neutral center (grey or white, without color) to two
different extreme colors (blue and red by default). For the
diverging HSL colors, again two hues :h are needed, a maximal
saturation ':s' and two lightnesses ':l'.  The colors are then created by
an interpolation between the full color hsl1,
a neutral color hsl and the other full color hsl2.

Arguments:
numcolors: Number of colors to be produced in this pallette.

Optional Arguments:
:h-start (keyword) starting hue (default 260)
:h-end (keyword) ending hue (default 0)
:s (keyword) saturation. 0.0 - 100.0 (default 80.0)
:l-start (keyword) starting lightness. 0.0 - 100.0. (default 30.0)
:l-end (keyword) ending lightness. 0.0 - 100.0. (default 90.0)
:power (keyword) control parameter determining how saturation and lightness should
be increased (1 = linear, 2 = quadratic, etc.) (default 1.5)
"

  [numcolors & opts]
  (let [opts (merge {:h-start 260 :h-end 0
                     :s 80.0
                     :l-start 30.0 :l-end 90.0
                     :power 1.5}
                    (when opts (apply assoc {} opts)))
        diff-l (- (:l-end opts) (:l-start opts))]
    (map #(create-color :h (if (> % 0) (:h-start opts) (:h-end opts))
                        :s (* (:s opts) (Math/pow (Math/abs %) (:power opts)))
                        :l (- (:l-end opts) (* diff-l (Math/pow (Math/abs %) (:power opts)))))
         (inclusive-seq numcolors -1.0 1.0))))


(defn sequential-hsl
  "Creates a sequential palette starting at the full color
 (h :s-start :l-start) through to a light color (h :s-end :l-end) by
interpolation.

Arguments:
numcolors: Number of colors to be produced in this pallette.

Optional Arguments:
:h (keyword) starting hue (default 260)
:s-start (keyword) starting saturation. 0.0 - 100.0 (default 80.0)
:l-start (keyword) starting lightness. 0.0 - 100.0. (default 30.0)
:s-end (keyword) ending saturation. 0.0 - 100.0 (default 0.0)
:l-end (keyword) ending lightness. 0.0 - 100.0. (default 90.0)
:power (keyword) control parameter determining how saturation and lightness should
be increased (1 = linear, 2 = quadratic, etc.)
"

  [numcolors & opts]
  (let [opts (merge {:h 260
                     :s-start 80.0 :l-start 30.0
                     :s-end 0.0 :l-end 90.0 :power 1.5}
                    (when opts (apply assoc {} opts)))
        diff-s (- (:s-end opts) (:s-start opts))
        diff-l (- (:l-end opts) (:l-start opts))]
    (map #(create-color :h (:h opts)
                        :s (- (:s-end opts) (* diff-s (Math/pow % (:power opts))))
                        :l (- (:l-end opts) (* diff-l (Math/pow % (:power opts)))))
         (inclusive-seq numcolors 1.0 0.0))))

(defn heat-hsl
  " Create heat pallette in HSL space. By default, it goes from a red to a yellow hue, while
simultaneously going to lighter colors (i.e., increasing
lightness) and reducing the amount of color (i.e., decreasing
saturation).

Arguments:
numcolors: Number of colors to be produced in this pallette.

Optional Arguments:
:h-start (keyword) starting hue (default 260)
:h-end (keyword) ending hue (default 260)
:s-start (keyword) starting saturation. 0.0 - 100.0 (default 80.0)
:l-start (keyword) starting lightness. 0.0 - 100.0. (default 30.0)
:s-end (keyword) ending saturation. 0.0 - 100.0 (default 0.0)
:l-end (keyword) ending lightness. 0.0 - 100.0. (default 90.0)
:power-saturation (keyword) control parameter determining how saturation should increase
:power-lightness (keyword) control parameter determining how lightness should increase
be increased (1 = linear, 2 = quadratic, etc.)
"

  [numcolors & opts]
  (let [opts (merge {:h-start 0 :h-end 90
                     :s-start 80.0 :l-start 30.0
                     :s-end 0.0 :l-end 90.0
                     :power-saturation 0.20 :power-lightness 1}
                    (when opts (apply assoc {} opts)))
        diff-h (- (:h-end opts) (:h-start opts))
        diff-s (- (:s-end opts) (:s-start opts))
        diff-l (- (:l-end opts) (:l-start opts))]
    (map #(create-color :h (- (:h-end opts) (* (- diff-h %)))
                        :s (- (:s-end opts) (* diff-s (Math/pow % (:power-saturation opts))))
                        :l (- (:l-end opts) (* diff-l (Math/pow % (:power-lightness opts)))))
         (inclusive-seq numcolors 1.0 0.0))))

(defn terrain-hsl
  "The 'terrain_hcl' palette simply calls 'heat_hcl' with
different parameters, providing suitable terrain colors."
  [numcolors & opts]
  (heat-hsl numcolors :h-start 0 :h-end 90
            :s-start 100.0 :s-end 30.0
            :l-start 50.0 :l-end 90.0))

(def html4-colors-name-to-rgbnum
     {
      "black"    0x000000
      "silver"   0xc0c0c0
      "gray"     0x808080
      "white"    0xffffff
      "maroon"   0x800000
      "red"      0xff0000
      "purple"   0x800080
      "fuchsia"  0xff00ff
      "green"    0x008000
      "lime"     0x00ff00
      "olive"    0x808000
      "yellow"   0xffff00
      "navy"     0x000080
      "blue"     0x0000ff
      "teal"     0x008080
      "aqua"     0x00ffff
      })

(def html4-colors-rgbnum-to-name
     (into {} (map (fn [[k v]] [v k]) html4-colors-name-to-rgbnum)))

(def html4-colors-name-to-rgb
     (into {} (for [[k v] html4-colors-name-to-rgbnum] [k (rgb-int-to-components v)])))

(def html4-colors-rgb-to-name
     (into {} (map (fn [[k v]] [v k]) html4-colors-name-to-rgb)))

(defn hue-to-rgb
  "Convert hue color to rgb components
Based on algorithm described in:
http://en.wikipedia.org/wiki/Hue#Computing_hue_from_RGB
and:
http://www.w3.org/TR/css3-color/#hsl-color"
  [m1, m2, hue]
  (let* [h (cond
           (< hue 0) (inc hue)
           (> hue 1) (dec hue)
           :else hue)]
        (cond
         (< (* h 6) 1) (+ m1 (* (- m2 m1) h 6))
         (< (* h 2) 1) m2
         (< (* h 3) 2) (+ m1 (* (- m2 m1) (- (/ 2.0 3) h) 6))
         :else m1)))

(defn hsl-to-rgb
  "Given color with HSL values return vector of r, g, b.

Based on algorithms described in:
http://en.wikipedia.org/wiki/Luminance-Hue-Saturation#Conversion_from_HSL_to_RGB
and:
http://en.wikipedia.org/wiki/Hue#Computing_hue_from_RGB
and:
http://www.w3.org/TR/css3-color/#hsl-color"
  [hue saturation lightness]
  (let* [h (/ hue 360.0)
         s (/ saturation 100.0)
         l (/ lightness 100.0)
         m2 (if (<= l 0.5) (* l (+ s 1))
                (- (+ l s) (* l s)))
         m1 (- (* l 2) m2)]
        (into []
              (map #(round (* 0xff %))
                   [(hue-to-rgb m1 m2 (+ h (/ 1.0 3)))
                    (hue-to-rgb m1 m2 h)
                    (hue-to-rgb m1 m2 (- h (/ 1.0 3)))]))))
  (defn rgb-to-hsl
    "Given the three RGB values, convert to HSL and return vector of
  Hue, Saturation, Lightness.

Based on algorithm described in:
http://en.wikipedia.org/wiki/Luminance-Hue-Saturation#Conversion_from_RGB_to_HSL_overview"
  [red green blue]
  (let* [r (/ red 255.0)
         g (/ green 255.0)
         b (/ blue 255.0)
         min (min r g b)
         max (max r g b)
         delta (- max min)
         l (/ (+ max min) 2.0)
         h (condp = max
                  min 0.0
                  r (* 60 (/ (- g b) delta))
                  g (+ 120 (* 60 (/ (- b r) delta)))
                  b (+ 240 (* 60 (/ (- r g) delta))))
         s (cond
            (= max min) 0.0
            (< l 0.5) (/ delta (* 2 l))
            :else (/ delta (- 2 (* 2 l))))]
        [(mod h 360.0) (* 100.0 s) (* 100.0 l)]))