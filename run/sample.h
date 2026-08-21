#include <iostream>
#include <iostream>

int main() {
     int asdf = "asd";
     return "f";
}

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

void testCStyleCast() {
//     int (std::MyClass::*methodPtr)(const std::string&) const;

//     int b1 = (int)3.14f;
//     void* raw_ptr = &b1;
//     const char* b2 = (const char*)raw_ptr;
//     unsigned long b3 = (unsigned long int)42;
//     void* b4 = (void*)0;
//     volatile int* b5 = (volatile int*)raw_ptr;
//
//     // ==========================================
//     // 2. Цепочки кастов (Right-Associativity)
//     // ==========================================
//     void* c1 = (void*)(char*)(int*)raw_ptr;
//     int c2 = (int)(double)(float)b1;
//
//     // ==========================================
//     // 3. Различение каста и скобок выражения
//     // ==========================================
//     int a = 10, b = 20;
//     int e1 = (int)(a + b);
//     int e2 = (int)a + b;        // Применяется только к 'a'
//     int e3 = (int)-a;
//
//     void* ptr_buf = &raw_ptr;
//     // * (void**)ptr_buf дает void*, затем кастуется к char*
//     char* e4 = (char*)*(void**)ptr_buf;
//     int e5 = ((int)a);
//
//     // ==========================================
//     // 4. Приоритеты операторов
//     // ==========================================
//     int p1 = (int)get_val();    // Каст результата функции
//
//     int arr[5] = {65, 66, 67, 68, 69};
//     char p2 = (char)arr[0];     // Каст элемента массива
//
//     int x = 5;
//     int p3 = (int)x++;          // Каст результата постфиксного инкремента
//
//     int val = 100;
//     void* void_val_ptr = &val;
//     int p4 = *(int*)void_val_ptr; // Сначала (int*), затем разыменование *
//
//     int* val_ptr = &val;
//     int p5 = (int)*val_ptr;      // Сначала разыменование *, затем каст (int)
//
//     // Исправление: кастуем к ссылке char&, чтобы получить lvalue и взять адрес &
//     char* p6 = &(char&)x;
//
//     // ==========================================
//     // 5. Сложные абстрактные деклараторы
//     // ==========================================
//     // 1. Указатель на массив из 10 элементов: (int(*)[10])
//     int matrix[10] = {0};
//     void* raw_arr = matrix;
//     int(*a1)[10] = (int(*)[10])raw_arr;
//
//     // 2. Указатель на функцию: (void(*)(int, double))
//     void* raw_fn = (void*)&sample_func;
//     void(*a2)(int, double) = (void(*)(int, double))raw_fn;
//
//     // 3. Многоуровневые указатели с квалификаторами
//     const char* str = "hello";
//     const char* const volatile* str_ptr = &str;
//     const char* const volatile* a3 = (const char* const volatile*)str_ptr;
//
//     // 4. Указатель на функцию, возвращающую указатель: (int*(*)())
//     void* raw_fn_ret = (void*)&sample_func_returning_ptr;
//     int*(*a4)() = (int*(*)())raw_fn_ret;
//
//     // 5. Каст адреса функции и её немедленный вызов
//     void* raw_fn_int = (void*)&sample_func_int;
//     int a5 = ((int(*)(int))raw_fn_int)(42);
}

void testEscapeSequences() {
    int a = 'a';
    auto c = &a;
    c = 2.0;


    c = 2.0;
    int x,y = 5,z(double);

    int ref = 1 + 5;

    int (*(*arr[3])(int, char))[5];

    auto s5 = "Hello, " U"World!";

    char c4 = '\\';

    char c1 = '\n';
    char c2 = '\t';
    char c3 = '\a';
    char c5 = '\'';

    char o1 = '\0';
    char o2 = '\101';
    const char* os1 = "\1012";

    char h1 = '\x0';
    char h2 = '\x41';
    char h3 = '\xFF';
    char h4 = '\x7F';

    const char16_t* u1 = u"\u0410";
    const char32_t* u2 = U"\U0001F600";

    int m1 = 'ab';
    int m2 = 'abcd';

    const wchar_t* s1 = L"Wide";
    const char8_t* s2 = u8"UTF-8";
}


void testInvalidStrings() {
//     auto e6 = 'A' "hello";
//
//     auto ch = 'A';
//
//     auto e1 = L"Wide" u8"UTF8";
//     auto e2 = u"UTF16" U"UTF32";
//     auto e3 = L"Wide" u"UTF16";
//     auto e4 = LR"(Raw Wide)" u8R"(Raw UTF8)";
//
//     auto e5 = "Hello, " + "World!";
//
//     auto e7 = "hello" 'B';
//
//     auto e8 = u8"start" "middle" L"end";
}



void testPointerTypes() {
//     int (std::MyClass::*methodPtr)(const std::string&) const;


    int x= 1;
    int*&& ref = &x;

    short a4;

    int* arrOfPtrs[10];

    const int* p1 = &x;

    // 2. Константный указатель на int
    int* const p2 = &x;

    // 3. Константный указатель на константный int
    const int* const p3 = &x;

    // 4. Двойной указатель с разным уровнем const
    const int* const* const p4 = &p3;

    // 5. Rvalue-ссылка (C++11) на указатель
    void (*signal(int sig, void (*func)(int)))(int);
    int (*foo(char c))[10];
    int (*arr[5])(double);
}

void testStrings() {
    auto s1 = "Hello, " "World!";

    auto s2 = "Hello, " L"World!";
    auto s3 = "Hello, " u8"World!";
    auto s4 = "Hello, " u"World!";

    auto s6 = L"Wide " L"string " L"test";
    auto s7 = u8"UTF-8 " u8"multiline " u8"string";

//NOT SUPPORTED YET
//     auto s8 = "Line 1\n" R"(Line 2)";
//     auto s9 = u8"Start " u8R"(Raw UTF-8)";
}
//
// class ConstructorTest {
//
//     ConstructorTest(int hp) : horsepower(hp), test(hp) {}
// }
//
//
// struct TestClass {
//
//     // ------------------------------------------------------------------------
//     // 1. Обычные методы с различными комбинациями CV-квалификаторов
//     // ------------------------------------------------------------------------
//     void m_plain();
//     void m_const() const;
//     void m_volatile() volatile;
//     void m_cv() const volatile;
//     void m_vc() volatile const; // Валидно: порядок const и volatile неважен
//
//     // ------------------------------------------------------------------------
//     // 2. Ref-квалификаторы (& и &&)
//     // ------------------------------------------------------------------------
//     void m_lvalue() &;
//     void m_rvalue() &&;
//     void m_const_lvalue() const &;
//     void m_cv_rvalue() const volatile &&;
//
//     // ------------------------------------------------------------------------
//     // 3. Спецификатор noexcept
//     // ------------------------------------------------------------------------
//     void m_noexcept() noexcept;
//     void m_const_noexcept() const noexcept;
//     void m_ref_noexcept() & noexcept;
//
//     // Все квалификаторы в максимальной комбинации (CV -> Ref -> noexcept)
//     void m_full_monster(int x, double y) const volatile && noexcept;
// };
//
// void testSizeOf() {
//     int size1 = sizeof(std::string);
//     int size2 = sizeof(size2);
// }
//
// void testNewExpr() {
//     int test = new (buffer) int(1);
//     int test1 = new int(1);
//     int test2 = new int{1};
//     int test2 = new int[10]{1};
//     int test2 = new int[]{1};
//     int test2 = new int[];
//     int test2 = new int;
// }
//
// // 1. A function that does not return anything (void) and takes no parameters
// void greetUser() {
//     std::cout << "Welcome to the C++ Function Tutorial!" u8"asdasdasd" u8"asdasd" << std::endl;
// }
//
// void testPrimitiveTypes() {
//     int a1;
//     signed int a2;
//     unsigned int a3;
//     short a4;
//     short int a5;
//     signed short a6;
//     unsigned short a7;
//     unsigned short int a8;
//
//     // Довгі цілочисельні типи
//     long b1;
//     long int b2;
//     unsigned long b3;
//     long long b4;
//     long long int b5;
//     unsigned long long b6;
//     unsigned long long int b7;
//
//     // Символьні типи
//     char c1;
//     signed char c2;
//     unsigned char c3;
//
//     // Дробові типи
//     float d1;
//     double d2;
//     long double d3;
//
//     // Логічний та порожній типи
//     bool e1;
//     void e2;
//
//
// }
//
//
//
// void testUnaryOperators() {
//     int x = 10;
//     int y = 20;
//     int ptr = &x;
//
//
//     // 1. Базові префіксні та постфіксні
//     +x;
//     -y;
//     !x;
//     ~x;
//     x++;
//     y--;
//     ++x;
//     --y;
//
//     // 2. Унарні оператори + Бінарні (контекстні тести)
//     int sum = a + -b;
//     int mult = *ptr * *ptr;
//     int addr = a & &b;
//
//     // 3. Каскадні унарні оператори
//     !!x;
//     - -x;
//     *&x;
//
//     // 4. Глобальний scope та розвказівка зі стрілкою
//     ::x;
//     *ptr->field;
//
//     // 5. Поєднання у виразах
//     int result = ++x * y--;
// }
//
// class Vector2D {
// private:
//     float x = 0.0f;
//     float y = 3.14159;
//     int id = 100;
//
// public:
//     void update(float dx, int step) {
//         if (dx >= 1.5 && step != 0) {
//             x += dx * 2.0;
//             this->x = 1;
//             id--;
//         } else {
//             std::string msg = "Status: \"OK\"\n";
//         }
//     }
// };
//
//
//
// // 2. A function that takes parameters and returns a value (int)
// int addNumbers(int num1, int num2) {
//     int sum = num1 + (num2 + (num1 + num2));
//     return sum;
// }
//
// // 3. A function that uses a default parameter value
// void displayScore(int score, std::string name = "Player") {
//     std::cout << name << " has a score of: " << score << std::endl;
// }
//
// int main() {
//     // Calling the void function
//
//     // Calling the addition function and storing its returned value
//     int result = addNumbers(15, 27);
//
//     for (int i = 0; i < 10; i+=2) {
//         int x = i * 2;
//     }
//
//     for (int i = 0, j = 10; i < j; i+=2, j+=2) {
//         int sum = i + j;
//     }
//
//     int i = 0;
//     for (; i < 5; i+=2) {
//         i = i + 1;
//     }
//
//     for (;;) {
//         break;
//     }
//
//     if(result >= 10 || result != 1) {
//         greetUser();
//     } else if(result == 2) {
//         result = 10;
//     } else {
//         result = 5;
//     }
//
//
//     while (i < 10) {
//         i = i + 1;
//         int x = i * 2;
//     }
//
//     while (x > 0)
//         x = x - 1;
//
//     while (fetchData());
//
//    while (i < 10) {
//         i = i + 1;
//         int x = i * 2;
//    }
//
//    do {
//         i = i + 1;
//         int x = i * 2;
//    } while (i < 10);
//
//     std::cout << "The sum of 15 and 27 is: " << result << std::endl;
//
//     // Calling a function with a default parameter
//     displayScore(100);                 // Uses default name "Player"
//     displayScore(250, "Alex");         // Overrides default name with "Alex"
//
//     return 0;
// }