#include <stdio.h>
#include <string.h>
#include <ctype.h>

void getGreeting(char *result, const char *name) {
    sprintf(result, "Hello, %s!", name);
}

void getFarewell(char *result, const char *name) {
    sprintf(result, "Goodbye, %s. Have a great day!", name);
}

void toLower(char *str) {
    for (int i = 0; str[i]; i++) {
        str[i] = tolower(str[i]);
    }
}

void getPersonalizedGreeting(char *result, const char *name, const char *timeOfDay) {
    char timeOfDayLower[50];
    strcpy(timeOfDayLower, timeOfDay);
    toLower(timeOfDayLower);
    if (strcmp(timeOfDayLower, "morning") == 0) {
        sprintf(result, "Good morning, %s", name);
    } else if (strcmp(timeOfDayLower, "afternoon") == 0) {
        sprintf(result, "Good afternoon, %s", name);
    } else if (strcmp(timeOfDayLower, "evening") == 0) {
        sprintf(result, "Good evening, %s", name);
    } else {
        sprintf(result, "Good day, %s", name);
    }
}

int main() {
    char result[100];
    getGreeting(result, "foo");
    printf("%s\\n", result);
    getFarewell(result, "bar");
    printf("%s\\n", result);
    getPersonalizedGreeting(result, "baz", "morning");
    printf("%s\\n", result);
    return 0;
}
