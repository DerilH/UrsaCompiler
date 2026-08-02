#include <iostream>

#include <iostream>
#include <vector>
#include <string>

#define MAX_SIZE 0xFF
#define EPSILON 1.0e-5f

/*
 * Многострочный комментарий
 * Внутри могут быть символы: //, /*, ->, >=
 * И даже неполные кавычки "hello
 */

// template <typename T, int N = 100>
// class ComplexParserTest {
// private:
//     double buffer[4] = { .5, 3.14159e+2, 0b101101, 0755 };
//     std::string text = "Строка с экранированием:\t\"Hello,\n\t\\World!\"";
//     char newline = '\n';
//
// public:
//     auto process(const T& val, T* ptr) -> decltype(ptr) {
//         // Однострочный комментарий с кавычкой " и сложением +
//         if (ptr != nullptr && (val >= 0.5 || val <= -1e-3)) {
//             *ptr += val * static_cast<T>(MAX_SIZE);
//         }
//
//         bool isValid = (val != 0) && (!ptr || *ptr == 0);
//
//         // Сложные операторы и сдвиги
//         int bitshift = 1 << 4;
//         bitshift >>= 2;
//
//         return ptr;
//     }
// };
//
// int main() {
//     ComplexParserTest<std::vector<int>> testObj;
//
//     int a = 5;
//     int b = 10;
//     // Граничный случай: как лексер разберет a+++b? (a++ + b)
//     int c = a+++b;
//
//     return 0;
// }

void testPointerTypes() {
    int* arrOfPtrs[10];

    const int* p1 = &x;

    // 2. Константный указатель на int
    int* const p2 = &x;

    // 3. Константный указатель на константный int
    const int* const p3 = &x;

    // 4. Двойной указатель с разным уровнем const
    const int* const* const p4 = &p3;

    // 5. Rvalue-ссылка (C++11) на указатель
    int*&& ref = &x;
    }

void testSizeOf() {
    int size1 = sizeof(std::string);
    int size2 = sizeof(size2);
}

void testNewExpr() {
    int test = new (buffer) int(1);
    int test1 = new int(1);
    int test2 = new int{1};
    int test2 = new int[10]{1};
    int test2 = new int[]{1};
    int test2 = new int[];
    int test2 = new int;
}

// 1. A function that does not return anything (void) and takes no parameters
void greetUser() {
    std::cout << "Welcome to the C++ Function Tutorial!" << std::endl;
}

void testPrimitiveTypes() {
    int a1;
    signed int a2;
    unsigned int a3;
    short a4;
    short int a5;
    signed short a6;
    unsigned short a7;
    unsigned short int a8;

    // Довгі цілочисельні типи
    long b1;
    long int b2;
    unsigned long b3;
    long long b4;
    long long int b5;
    unsigned long long b6;
    unsigned long long int b7;

    // Символьні типи
    char c1;
    signed char c2;
    unsigned char c3;

    // Дробові типи
    float d1;
    double d2;
    long double d3;

    // Логічний та порожній типи
    bool e1;
    void e2;


}



void testUnaryOperators() {
    int x = 10;
    int y = 20;
    int ptr = &x;


    // 1. Базові префіксні та постфіксні
    +x;
    -y;
    !x;
    ~x;
    x++;
    y--;
    ++x;
    --y;

    // 2. Унарні оператори + Бінарні (контекстні тести)
    int sum = a + -b;
    int mult = *ptr * *ptr;
    int addr = a & &b;

    // 3. Каскадні унарні оператори
    !!x;
    - -x;
    *&x;

    // 4. Глобальний scope та розвказівка зі стрілкою
    ::x;
    *ptr->field;

    // 5. Поєднання у виразах
    int result = ++x * y--;
}

class Vector2D {
private:
    float x = 0.0f;
    float y = 3.14159;
    int id = 100;

public:
    void update(float dx, int step) {
        if (dx >= 1.5 && step != 0) {
            x += dx * 2.0;
            this->x = 1;
            id--;
        } else {
            std::string msg = "Status: \"OK\"\n";
        }
    }
};



// 2. A function that takes parameters and returns a value (int)
int addNumbers(int num1, int num2) {
    int sum = num1 + (num2 + (num1 + num2));
    return sum;
}

// 3. A function that uses a default parameter value
void displayScore(int score, std::string name = "Player") {
    std::cout << name << " has a score of: " << score << std::endl;
}

int main() {
    // Calling the void function

    // Calling the addition function and storing its returned value
    int result = addNumbers(15, 27);

    for (int i = 0; i < 10; i+=2) {
        int x = i * 2;
    }

    for (int i = 0, j = 10; i < j; i+=2, j+=2) {
        int sum = i + j;
    }

    int i = 0;
    for (; i < 5; i+=2) {
        i = i + 1;
    }

    for (;;) {
        break;
    }

    if(result >= 10 || result != 1) {
        greetUser();
    } else if(result == 2) {
        result = 10;
    } else {
        result = 5;
    }


    while (i < 10) {
        i = i + 1;
        int x = i * 2;
    }

    while (x > 0)
        x = x - 1;

    while (fetchData());

   while (i < 10) {
        i = i + 1;
        int x = i * 2;
   }

   do {
        i = i + 1;
        int x = i * 2;
   } while (i < 10);

    std::cout << "The sum of 15 and 27 is: " << result << std::endl;

    // Calling a function with a default parameter
    displayScore(100);                 // Uses default name "Player"
    displayScore(250, "Alex");         // Overrides default name with "Alex"

    return 0;
}