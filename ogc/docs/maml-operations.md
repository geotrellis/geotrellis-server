## List of available MAML operations

Below is a list of all the currently supported MAML operations. The
'Operation' column is an english description of the encoded operation, the
'Symbol' column represents the symbol used when serializing and
deserializing a MAML AST, and the 'Case Class' column represents the
class, in source code, which MAML uses to represent the operation.

| Operation                  | Symbol     | Case Class      |
|----------------------------|------------|-----------------|
| Addition                   | +          | Addition        |
| Subtraction                | -          | Subtraction     |
| Multiplication             | *          | Multiplication  |
| Division                   | /          | Division        |
| Max                        | max        | Max             |
| Min                        | min        | Min             |
| Mask                       | mask       | Mask            |
| Exponentiation             | \*\*         | Pow             |
| Less than                  | <          | Lesser          |
| Less than or equal to      | <=         | LesserOrEqual   |
| Inequality                 | !=         | Unequal         |
| Equality                   | =          | Equal           |
| Greater than or equal to   | >=         | GreaterOrEqual  |
| Greater than               | >          | Greater         |
| Or                         | or         | Or              |
| Xor                        | xor        | Xor             |
| And                        | and        | And             |
| Two argument Atan          | atan2      | Atan2           |
| If/Else                    | ifelse     | Branch          |
| Classify                   | classify   | Classification  |
| Sin                        | sin        | Sin             |
| Cos                        | cos        | Cos             |
| Tan                        | tan        | Tan             |
| Sinh                       | sinh       | Sinh            |
| Cosh                       | cosh       | Cosh            |
| Tanh                       | tanh       | Tanh            |
| Asin                       | asin       | Asin            |
| Acos                       | acos       | Acos            |
| Atan                       | atan       | Atan            |
| Round                      | round      | Round           |
| Floor                      | floor      | Floor           |
| Ceil                       | ceil       | Ceil            |
| Natural log                | loge       | LogE            |
| Log 10                     | log10      | Log10           |
| Square root                | sqrt       | SquareRoot      |
| Absolute value             | abs        | Abs             |
| Is defined (predicate)     | def        | Defined         |
| Is not defined (predicate) | undef      | Undefined       |
| Numeric negation           | nneg       | NumericNegation |
| Logical negation           | lneg       | LogicalNegation |
| Focal maximum              | fmax       | FocalMax        |
| Focal minimum              | fmin       | FocalMin        |
| Focal mean                 | fmean      | FocalMean       |
| Focal median               | fmedian    | FocalMedian     |
| Focal mode                 | fmode      | FocalMode       |
| Focal sum                  | fsum       | FocalSum        |
| Focal standard deviation   | fstddev    | FocalStdDev     |
| Focal slope                | fslope     | FocalSlope      |
| Focal hillshade            | fhillshade | FocalHillshade  |
| Image selection            | sel        | ImageSelect     |
| Int literal                | int        | IntLit          |
| Int variable               | intV       | IntVar          |
| Double literal             | dbl        | DblLit          |
| Double variable            | dblV       | DblVar          |
| Boolean literal            | bool       | BoolLit         |
| Boolean variable           | boolV      | BoolVar         |
| Geometry literal           | geom       | GeomLit         |
| Geometry variable          | geomV      | GeomVar         |
| Raster variable            | rasterV    | RasterVar       |


