# Compose Clean Architecture Template

A modern, scalable, and responsive Android application template built with Jetpack Compose using Clean Architecture and the MVI (Model-View-Intent) pattern.

## 🛠️ Git Workflow & Conventions

### 1. Initial Setup (Git Hooks)

Before making any commits, set up the git hooks to enforce code quality/conventions:

```bash
# Make the script executable
chmod +x ./setup.sh

# Run the setup
./setup.sh
```

### 2. Git Branch & Commit Conventions

#### Branch Naming
All branches must follow the format:
- `feat/<feature-name>`
- `fix/<bug-name>`
- `refactor/<refactor-name>`
- `chore/<task-name>`

#### Commit Messages
All commits should follow the Conventional Commit format:

```text
type: Short description (at least 3 characters)
```

**Allowed types:** `feat`, `fix`, `refactor`, `chore`, `docs`, `style`, `test`, `perf`, `ci`, `build`, `wip`, `revert`

**Examples:**
- `feat: add localization support`
- `fix: correct padding on lunch card`

## 🚀 Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Navigation**: [Voyager](https://voyager.adriel.cafe/) (Screens, ScreenModels, TabNavigator, BottomSheetNavigator)
- **Dependency Injection**: [Koin](https://insert-koin.io/) with KSP Annotations
- **Asynchronous Programming**: [Kotlin Coroutines & Flow](https://kotlinlang.org/docs/coroutines-overview.html)
- **Networking**: [Retrofit 2](https://square.github.io/retrofit/) & OkHttp + Gson
- **Architecture**: Clean Architecture + MVI

---

## 🏗️ Project Structure

The project is strictly structured following Clean Architecture principles, ensuring separation of concerns:

- **`core/`**: Shared utilities used across all layers.
  - Contains constants, base state/status classes (`BaseStatus`), error handling, extension functions, and custom configurations (e.g., layout adaptability helpers).
- **`data/`**: The data layer handling all external data interactions.
  - `data_source/`: Remote (API) or Local (Database/DataStore) data sources.
  - `models/`: Data Transfer Objects (DTOs) and mappers to convert DTOs into Domain Entities.
  - `repository_impl/`: Concrete implementations of domain repository interfaces.
- **`domain/`**: The purest layer of the application containing business logic. It does not know anything about Android or APIs.
  - `entity/`: Core business models.
  - `repository/`: Interfaces defining data operations.
  - `use_cases/`: Use cases handling specific business logic rules.
- **`presentation/`**: The UI layer using Jetpack Compose.
  - `screens/`: Voyager Screens representation.
  - `components/`: Reusable Compose UI components.
  - `viewModel/` (or ScreenModels): Presentation logic and state management using Voyager's `ScreenModel`.
- **`ui/`**: Global UI configurations, Theme, Colors, and Typography.

---

## 📖 Conventions & Getting Started

### 1. Dependency Injection (Koin Annotations)
This project uses **Koin KSP Compiler**. You do not need to write manual Koin modules.
Instead, use annotations on your classes:
- `@Single`: For singletons (e.g., Repositories, APIs).
- `@Factory`: For new instances per injection (e.g., UseCases, ScreenModels).

```kotlin
@Single
class UserListRepositoryImpl(private val api: UserApi) : UserListRepository { ... }

@Factory
class GetUserListUseCase(private val repository: UserListRepository) { ... }
```

### 2. Adding a New Feature (Step-by-Step)

When adding a new feature (e.g., "Product Details"):

#### Step 1: Domain Layer
1. Create models in `domain/entity/Product.kt`.
2. Define interface in `domain/repository/ProductRepository.kt`.
3. Create use cases in `domain/use_cases/GetProductDetailsUseCase.kt`.

#### Step 2: Data Layer
1. Create network DTOs in `data/models/ProductDto.kt` and write a mapper `ProductDto.toEntity()`.
2. Define API interface in `data/data_source/ProductApi.kt`.
3. Implement the repository in `data/repository_impl/ProductRepositoryImpl.kt` and annotate it with `@Single`.

#### Step 3: Presentation Layer (MVI & Voyager)
1. Provide UI State data class `ProductState.kt`. Include `BaseStatus` to handle Loading, Success, and Failure natively:
   ```kotlin
   data class ProductState(
       val status: BaseStatus<Product> = BaseStatus.None
   )
   ```
2. Create your ScreenModel extending `ScreenModel` and annotate it with `@Factory`:
   ```kotlin
   @Factory
   class ProductScreenModel(
       private val getProductDetailsUseCase: GetProductDetailsUseCase
   ) : ScreenModel {
       // Expose StateFlow
       private val _state = MutableStateFlow(ProductState())
       val state: StateFlow<ProductState> = _state.asStateFlow()
       
       fun loadProduct() {
           screenModelScope.launch { ... }
       }
   }
   ```
3. Create the Composer Screen extending `AppScreen` (which handles responsive layouts internally):
   ```kotlin
   class ProductScreen : AppScreen {
       @Composable
       override fun BuildCompact() {
           val screenModel = getScreenModel<ProductScreenModel>()
           val state by screenModel.state.collectAsState()
           
           // Based on state.status, show loading, error, or data for phone layout
       }

       @Composable
       override fun BuildTablet() {
           // Provide tablet-specific layout here, or omit if identical to BuildCompact
       }

       @Composable
       override fun BuildDesktop() {
           // Provide desktop-specific layout here, or omit if identical to BuildTablet
       }
   }
   ```

### 3. Navigation & Screen Transitions
The project leverages [Voyager](https://voyager.adriel.cafe/) for seamless navigation. 
To navigate forward or backward, obtain the `Navigator` within your `@Composable`:

```kotlin
val navigator = LocalNavigator.currentOrThrow

// Navigate to a new screen
navigator.push(ProductScreen(productId = 123))

// Go back
navigator.pop()
```

### 4. Responsiveness
This template embraces adaptability:
- Use `AdaptiveHelper.kt` to observe window sizes or device types.
- Ensure your UI looks consistent on Handsets, Tablets, and Foldables.

---

## 🛠️ Build and Testing Instructions
1. Open the project in **Android Studio**.
2. Sync the project with Gradle Files. Since KSP is used, ensure the IDE triggers a fresh build to generate the Koin DI bindings.
3. Build & Run the app on an Emulator or Physical Device.
