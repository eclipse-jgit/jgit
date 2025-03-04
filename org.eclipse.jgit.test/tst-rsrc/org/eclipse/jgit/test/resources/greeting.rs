struct Greeting;

impl Greeting {
    fn get_greeting(&self, name: &str) -> String {
        format!("Hello, {}!", name)
    }

    fn get_farewell(&self, name: &str) -> String {
        format!("Goodbye, {}. Have a great day!", name)
    }

    fn get_personalized_greeting(&self, name: &str, time_of_day: &str) -> String {
        match time_of_day.to_lowercase().as_str() {
            "morning" => format!("Good morning, {}", name),
            "afternoon" => format!("Good afternoon, {}", name),
            "evening" => format!("Good evening, {}", name),
            _ => format!("Good day, {}", name),
        }
    }
}

fn main() {
    let greeting = Greeting;
    println!("{}", greeting.get_greeting("foo"));
    println!("{}", greeting.get_farewell("bar"));
    println!("{}", greeting.get_personalized_greeting("baz", "morning"));
}
