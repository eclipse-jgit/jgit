class Greeting:
    def get_greeting(self, name):
        greeting_message = f"Hello, {name}!"
        return greeting_message

    def get_farewell(self, name):
        farewell_message = f"Goodbye, {name}. Have a great day!"
        return farewell_message

    def get_personalized_greeting(self, name, time_of_day):
        time_of_day = time_of_day.lower()
        if time_of_day == "morning":
            personalized_message = f"Good morning, {name}"
        elif time_of_day == "afternoon":
            personalized_message = f"Good afternoon, {name}"
        elif time_of_day == "evening":
            personalized_message = f"Good evening, {name}"
        else:
            personalized_message = f"Good day, {name}"
        return personalized_message

if __name__ == "__main__":
    greeting = Greeting()
    print(greeting.get_greeting("foo"))
    print(greeting.get_farewell("bar"))
    print(greeting.get_personalized_greeting("baz", "morning"))
