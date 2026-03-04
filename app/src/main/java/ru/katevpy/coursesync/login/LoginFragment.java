package ru.katevpy.coursesync.login;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

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

        binding.button.setOnClickListener(v -> {
            binding.button.setEnabled(false);
            String email = binding.email.getText() != null ? binding.email.getText().toString() : "";
            String code = binding.code.getText() != null ? binding.code.getText().toString() : "";
            viewModel.onMainButtonClicked(email, code);
        });

        viewModel.getUi().observe(getViewLifecycleOwner(), state -> {
            if (state.step == LoginViewModel.Step.ENTER_EMAIL) {
                binding.code.setVisibility(View.GONE);
                binding.button.setText("send code");
            } else if (state.step == LoginViewModel.Step.ENTER_CODE) {
                binding.code.setVisibility(View.VISIBLE);
                binding.button.setText("verify");
            }

            binding.button.setEnabled(!state.loading);

            binding.email.setError(null);
            binding.code.setError(null);

            if (state.emailError != null) binding.email.setError(state.emailError);
            if (state.codeError != null) binding.code.setError(state.codeError);

            if (state.message != null) {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show();
            }

            if (state.navigateToApp) {
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