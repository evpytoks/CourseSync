package ru.katevpy.coursesync.login;

import android.os.Bundle;
import android.view.View;

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
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentLoginBinding.bind(view);
        viewModel = new ViewModelProvider(this)
                .get(LoginViewModel.class);

        binding.button.setOnClickListener(v ->
                viewModel.onSendCodeClicked()
        );

        viewModel.getStep().observe(
                getViewLifecycleOwner(),
                step -> {
                    if (step == LoginViewModel.Step.ENTER_EMAIL) {
                        binding.code.setVisibility(View.GONE);
                        binding.button.setText("send code");
                    } else if (step == LoginViewModel.Step.ENTER_CODE) {
                        binding.code.setVisibility(View.VISIBLE);
                        binding.button.setText("verify");
                    } else {
                        NavController navController = NavHostFragment.findNavController(this);
                        navController.navigate(R.id.action_loginFragment_to_groupsFragment);
                    }
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
