package ru.katevpy.coursesync.login;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.snackbar.Snackbar;

import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.databinding.FragmentLoginBinding;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private LoginViewModel viewModel;

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentLoginBinding.bind(view);

        viewModel = new ViewModelProvider(
                this,
                new LoginViewModelFactory(requireContext().getApplicationContext())
        ).get(LoginViewModel.class);

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        boolean handled = viewModel.onBackPressed();

                        if (!handled) {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                }
        );

        binding.button.setOnClickListener(v -> {
            binding.button.setEnabled(false);
            String email = binding.email.getText() != null ? binding.email.getText().toString() : "";
            String code = binding.code.getText() != null ? binding.code.getText().toString() : "";
            viewModel.onMainButtonClicked(email, code);
        });

        viewModel.getUi().observe(getViewLifecycleOwner(), state -> {
            if (state.step == LoginViewModel.Step.ENTER_EMAIL) {
                binding.code.setVisibility(View.GONE);
                binding.code.setText("");
                binding.button.setText(R.string.send_code);
            } else if (state.step == LoginViewModel.Step.ENTER_CODE) {
                binding.code.setVisibility(View.VISIBLE);
                binding.button.setText(R.string.verify_code);
            }

            binding.button.setEnabled(!state.loading);

            binding.email.setError(null);
            binding.code.setError(null);

            if (state.emailError != null) binding.email.setError(state.emailError);
            if (state.codeError != null) binding.code.setError(state.codeError);

            if (state.message != null) {
                if (state.message != null) {

                    if ("Код истёк. Отправьте новый".equals(state.message)) {

                        Snackbar.make(binding.getRoot(),
                                        state.message,
                                        Snackbar.LENGTH_INDEFINITE)
                                .setAction("OK", v -> {})
                                .show();

                    } else {

                        Snackbar.make(binding.getRoot(),
                                        state.message,
                                        Snackbar.LENGTH_LONG)
                                .show();
                    }
                }
            }

            if (state.navigateToApp) {
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).clearSelectedGroupAndPersist();
                }
                NavController navController = NavHostFragment.findNavController(this);
                navController.navigate(R.id.action_loginFragment_to_groupsFragment);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}