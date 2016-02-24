val Question = """(?i)(.*is.*it.*beer.*o'?clock.*)""".r
val Answer = """(?i)(.*(it's|its|it is).*beer.*o'?clock.*)""".r

val q = "its beer oclock"

val Its = """(?i).*(it's|its|it is).*""".r
val Beer = """(?i)(.*(it's|its|it is).*beer.*)""".r

q match {
  case Question(s) => "question: " + s
  case Answer(s) => "answer: " + s
  case Beer(s1, s2) => s"""beer: s1="$s1" s2="$s2""""
  case Its(s) => "its: " + s
}